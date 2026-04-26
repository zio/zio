/*
 * Copyright 2018-2024 John A. De Goes and ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLongFieldUpdater}
import scala.annotation.switch
import zio.{Chunk, Trace, Unsafe, ZIO}
import zio.internal.stacktracer.ZTraceElement

private[zio] object ZScheduler {
  private val DefaultMaxWorkers = 64
  private val DefaultMinWorkers = 1

  private val DefaultKeepAliveTimeMs = 60000L // 60 seconds

  private def parseTimeUnit(unit: String): TimeUnit = {
    (unit.toLowerCase: @switch) match {
      case "ms" | "milli" | "millis" | "milliseconds" => TimeUnit.MILLISECONDS
      case "s" | "sec" | "secs" | "second" | "seconds"  => TimeUnit.SECONDS
      case "m" | "min" | "mins" | "minute" | "minutes"  => TimeUnit.MINUTES
      case _                                           => TimeUnit.MILLISECONDS
    }
  }

  private def parseKeepAliveTime(property: String): Long = {
    val trimmed = property.trim
    if (trimmed.isEmpty) {
      return DefaultKeepAliveTimeMs
    }

    val digitsEnd = trimmed.indexWhere(c => !c.isDigit)
    if (digitsEnd == 0) {
      return DefaultKeepAliveTimeMs
    }

    val (numStr, unitStr) =
      if (digitsEnd == -1) (trimmed, "ms")
      else (trimmed.substring(0, digitsEnd), trimmed.substring(digitsEnd))

    try {
      val value = numStr.toLong
      val unit  = parseTimeUnit(unitStr)
      Math.max(0L, unit.toMillis(value))
    } catch {
      case _: NumberFormatException => DefaultKeepAliveTimeMs
    }
  }

  private val keepAliveTime: Long = {
    val property = System.getProperty("zio.keeper.keep-alive-time", "").trim
    if (property.isEmpty) DefaultKeepAliveTimeMs
    else parseKeepAliveTime(property)
  }

  private val maxWorkers: Int = {
    val property = System.getProperty("zio.keeper.max-threads", "")
    if (property.isEmpty) {
      DefaultMaxWorkers
    } else {
      try {
        val value = property.trim.toInt
        if (value < DefaultMinWorkers) DefaultMinWorkers
        else if (value > 65536) 65536
        else value
      } catch {
        case _: NumberFormatException => DefaultMaxWorkers
      }
    }
  }

  private val minWorkers: Int = {
    val property = System.getProperty("zio.keeper.min-threads", "")
    if (property.isEmpty) {
      DefaultMinWorkers
    } else {
      try {
        val value = property.trim.toInt
        if (value < 1) 1
        else if (value > maxWorkers) maxWorkers
        else value
      } catch {
        case _: NumberFormatException => DefaultMinWorkers
      }
    }
  }

  private val aggressiveUnparkThreshold: Int = {
    val property = System.getProperty("zio.keeper.aggressive-unpark-threshold", "")
    if (property.isEmpty) {
      1
    } else {
      try {
        val value = property.trim.toInt
        if (value < 0) 0
        else value
      } catch {
        case _: NumberFormatException => 1
      }
    }
  }
}

private[zio] final class ZScheduler private (
  val traceEnabled: Boolean,
  val reportFailure: (Throwable, Chunk[ZTraceElement]) => Unit
) extends zio.Executor with (() => Unit) {
  import ZScheduler._

  private[this] val runningCount = new AtomicInteger(0)
  private[this] val parkedWorkers = new ConcurrentLinkedQueue[Worker]()
  private[this] val workers = Array.tabulate[Worker](maxWorkers)(i => new Worker(i))

  private def submitTask(task: Runnable): Boolean = {
    val worker = Worker.currentWorker
    if (worker ne null) {
      worker.submit(task)
      true
    } else {
      false
    }
  }

  def apply(): Unit = {
    val worker = Worker.currentWorker
    if (worker ne null) {
      worker.run()
    }
  }

  def adjustThreadCount(requested: Int): Unit = {
    // No-op for now, could be extended for dynamic resizing
  }

  def metrics(implicit trace: Trace): ZIO[Any, Nothing, zio.ExecutorMetrics] =
    ZIO.succeed {
      zio.ExecutorMetrics(
        workers = workers.length,
        submittedTasks = 0L,
        completedTasks = 0L,
        hasFailures = false,
        utilization = 0.0
      )
    }

  def submit(runnable: Runnable, executor: zio.Executor): Boolean =
    if (executor eq this) submitTask(runnable)
    else executor.submit(runnable, this)

  def shutdown(): ZIO[Any, Nothing, Unit] =
    ZIO.succeed {
      // Signal all workers to stop
      workers.foreach(_.poison())
    }

  private def maybeUnparkWorker(): Unit = {
    val size = parkedWorkers.size()
    if (size > 0) {
      // Only unpark if we have more than the threshold of parked workers
      // This reduces the frequency of unpark calls, trading fairness for reduced overhead
      if (size >= aggressiveUnparkThreshold) {
        val worker = parkedWorkers.poll()
        if (worker ne null) {
          worker.unpark()
        }
      }
    }
  }

  private final class Worker(val id: Int) extends Runnable {
    private[this] val localQueue = new MutableConcurrentQueue[Runnable](128)
    private[this] var isShutdown  = false

    def submit(task: Runnable): Unit = {
      localQueue.offer(task)
      maybeUnparkWorker()
    }

    def poison(): Unit = {
      isShutdown = true
      unpark()
    }

    def unpark(): Unit = {
      LockSupport.unpark(this)
    }

    override def run(): Unit = {
      Worker.currentWorker = this
      runningCount.incrementAndGet()

      try {
        while (!isShutdown) {
          val task = localQueue.poll()
          if (task eq null) {
            // Park this worker
            parkedWorkers.offer(this)
            try {
              if (localQueue.isEmpty && !isShutdown) {
                LockSupport.parkNanos(keepAliveTime * 1000000L) // convert ms to nanos
              }
            } finally {
              parkedWorkers.remove(this)
            }
          } else {
            try {
              task.run()
            } catch {
              case t: Throwable =>
                reportFailure(t, Chunk.empty)
            }
          }
        }
      } finally {
        runningCount.decrementAndGet()
        Worker.currentWorker = null
      }
    }
  }

  // Start all workers
  {
    Unsafe.unsafe { implicit u =>
      workers.foreach { worker =>
        val thread = new Thread(worker)
        thread.setName(s"zio-fiber-runtime-worker-$worker.id")
        thread.setDaemon(true)
        thread.start()
      }
    }
  }
}

private[zio] object Worker {
  private val currentWorkerUpdater = AtomicLongFieldUpdater.newUpdater(classOf[Worker], "currentWorkerRef")
  @volatile private var currentWorkerRef: Long = 0L

  def currentWorker: Worker = {
    val ref = currentWorkerRef
    if (ref == 0L) null
    else Unsafe.fromRef[Worker](ref)
  }

  def currentWorker_=(worker: Worker): Unit = {
    currentWorkerUpdater.set(this, if (worker eq null) 0L else Unsafe.toRef(worker))
  }
}