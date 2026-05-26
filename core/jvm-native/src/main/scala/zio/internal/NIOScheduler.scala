/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.locks.LockSupport
import scala.collection.mutable
import scala.concurrent.{BlockContext, CanAwait}

/**
 * A `NIOScheduler` is an experimental least-loaded `Executor` inspired by the
 * Nio runtime scheduler.
 *
 * Unlike the work-stealing `ZScheduler`, externally submitted work is assigned
 * to the worker with the smallest observed backlog. This avoids the global
 * injector contention that can appear when many workers repeatedly steal from a
 * shared queue under high load.
 */
private final class NIOScheduler(autoBlocking: Boolean) extends Executor { parent =>

  import NIOScheduler.{poolSize, workerOrNull}
  import Trace.{empty => emptyTrace}

  private[this] val cache           = new ConcurrentLinkedQueue[NIOScheduler.Worker]()
  private[this] val globalLocations = makeLocations()
  private[this] val workers         = Array.ofDim[NIOScheduler.Worker](poolSize)

  @volatile private[this] var blockingLocations: Set[Trace] = Set.empty

  (0 until poolSize).foreach { workerId =>
    val worker = makeWorker()
    worker.setName(workerId)
    worker.setDaemon(true)
    workers(workerId) = worker
  }
  workers.foreach(_.start())

  if (autoBlocking) {
    val supervisor = makeSupervisor()
    supervisor.setName("NIOScheduler-Supervisor")
    supervisor.setDaemon(true)
    supervisor.start()
  }

  override private[zio] def isCurrentThreadInExecutor: Boolean =
    Thread.currentThread().isInstanceOf[NIOScheduler.Worker]

  def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] = {
    val metrics = new ExecutionMetrics {
      def capacity: Int =
        Int.MaxValue

      def concurrency: Int =
        poolSize

      def dequeuedCount: Long = {
        var dequeued = 0L
        var i        = 0
        while (i != poolSize) {
          dequeued += workers(i).opCount
          i += 1
        }
        dequeued
      }

      def enqueuedCount: Long = dequeuedCount + size.toLong

      def size: Int = {
        var size = 0
        var i    = 0
        while (i != poolSize) {
          size += workers(i).pending()
          i += 1
        }
        size
      }

      def workersCount: Int =
        poolSize
    }
    Some(metrics)
  }

  override def stealWork(depth: Int): Boolean = {
    val worker = workerOrNull()
    if ((worker ne null) && !worker.blocking) {
      val runnable = pollLocal(worker)

      if (runnable ne null) {
        runOnWorker(worker, runnable, depth)
        true
      } else {
        false
      }
    } else {
      false
    }
  }

  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = workerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      enqueue(selectWorker(), runnable)
      true
    }
  }

  override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = workerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else if ((worker ne null) && !worker.blocking && (worker.nextRunnable eq null) && worker.queue.isEmpty) {
      worker.nextRunnable = runnable
      true
    } else {
      enqueue(selectWorker(), runnable)
      true
    }
  }

  private[this] def enqueue(worker: NIOScheduler.Worker, runnable: Runnable): Unit = {
    worker.pendingCount.incrementAndGet()
    worker.queue.offer(runnable)
    if (worker.blocking) drain(worker)
    else wake(worker)
  }

  private[this] def wake(worker: NIOScheduler.Worker): Unit =
    if (!worker.active) {
      worker.active = true
      LockSupport.unpark(worker)
    }

  private[this] def pollLocal(worker: NIOScheduler.Worker): Runnable =
    if (worker.nextRunnable ne null) {
      val runnable = worker.nextRunnable
      worker.nextRunnable = null
      runnable
    } else {
      val runnable = worker.queue.poll()
      if (runnable ne null) worker.pendingCount.decrementAndGet()
      runnable
    }

  private[this] def drain(worker: NIOScheduler.Worker): Unit = {
    var runnable = pollLocal(worker)
    while (runnable ne null) {
      enqueue(selectWorker(), runnable)
      runnable = pollLocal(worker)
    }
  }

  private[this] def runOnWorker(worker: NIOScheduler.Worker, runnable: Runnable, depth: Int): Unit =
    if (runnable.isInstanceOf[FiberRunnable]) {
      val fiberRunnable = runnable.asInstanceOf[FiberRunnable]
      worker.currentRunnable = fiberRunnable
      fiberRunnable.run(depth)
    } else {
      worker.currentRunnable = runnable
      runnable.run()
    }

  private[this] def selectWorker(): NIOScheduler.Worker = {
    val from     = java.util.concurrent.ThreadLocalRandom.current().nextInt(poolSize)
    var selected = null.asInstanceOf[NIOScheduler.Worker]
    var load     = Int.MaxValue
    var i        = 0

    while ((i != poolSize) && (load != 0)) {
      val worker = workers((from + i) % poolSize)
      if (!worker.blocking) {
        val workerLoad = worker.pending()
        if (workerLoad < load) {
          selected = worker
          load = workerLoad
        }
      }
      i += 1
    }

    if (selected ne null) selected
    else workers(from)
  }

  private[this] def isBlocking(worker: NIOScheduler.Worker, runnable: Runnable): Boolean =
    if (autoBlocking && runnable.isInstanceOf[FiberRunnable]) {
      val fiberRunnable = runnable.asInstanceOf[FiberRunnable]
      val location      = fiberRunnable.location
      if ((location ne null) && (location ne emptyTrace)) {
        if (worker eq null) globalLocations.put(location)
        else worker.submittedLocations.put(location)
        blockingLocations.contains(location)
      } else false
    } else false

  private[this] def makeLocations(): NIOScheduler.Locations =
    if (autoBlocking) new NIOScheduler.Locations.Enabled
    else NIOScheduler.Locations.Disabled

  private[this] def makeSupervisor(): NIOScheduler.Supervisor =
    new NIOScheduler.Supervisor {

      private def countSubmittedAt(location: Trace): Long = {
        var count = globalLocations.get(location)
        var i     = 0
        while (i < poolSize) {
          count += workers(i).submittedLocations.get(location)
          i += 1
        }
        count
      }

      override def run(): Unit = {
        val identifiedLocations = makeLocations()
        val previousOpCounts    = Array.fill(poolSize)(-1L)
        while (!isInterrupted) {
          var workerId = 0
          while (workerId < poolSize) {
            val currentWorker = workers(workerId)
            if (currentWorker.active) {
              val currentOpCount  = currentWorker.opCount
              val previousOpCount = previousOpCounts(workerId)
              if (currentOpCount == previousOpCount) {
                val currentRunnable = currentWorker.currentRunnable
                if (currentRunnable.isInstanceOf[FiberRunnable]) {
                  val fiberRunnable = currentRunnable.asInstanceOf[FiberRunnable]
                  val location      = fiberRunnable.location
                  if (location ne emptyTrace) {
                    val identifiedCount = identifiedLocations.put(location)
                    val submittedCount  = countSubmittedAt(location)
                    if (submittedCount > 64 && identifiedCount >= submittedCount / 2) {
                      blockingLocations += location
                    }
                  }
                }
                previousOpCounts(workerId) = -1L
                currentWorker.markAsBlocking()
              } else {
                previousOpCounts(workerId) = currentOpCount
              }
            } else {
              previousOpCounts(workerId) = -1L
            }
            workerId += 1
          }
          val deadline = java.lang.System.currentTimeMillis() + 100
          var loop     = true
          while (loop) {
            LockSupport.parkUntil(deadline)
            loop = java.lang.System.currentTimeMillis() < deadline
          }
        }
      }
    }

  private[this] def makeWorker(): NIOScheduler.Worker =
    new NIOScheduler.Worker {
      self =>
      override val submittedLocations: NIOScheduler.Locations = makeLocations()

      final override def run(): Unit = {
        var currentOpCount = 0L
        var runnable       = null.asInstanceOf[Runnable]

        while (!isInterrupted) {
          if (!blocking) runnable = pollLocal(self)

          if (runnable eq null) {
            if (blocking) {
              drain(self)
              active = false
              cache.offer(self)
              while (!active && !isInterrupted) {
                LockSupport.park()
              }
            } else {
              active = false
              while (!active && !isInterrupted) {
                if ((nextRunnable ne null) || !queue.isEmpty) active = true
                else LockSupport.park()
              }
            }
          } else {
            active = true
            runOnWorker(self, runnable, 0)
            runnable = null
            currentRunnable = runnable
            currentOpCount += 1
            opCount = currentOpCount
          }
        }
      }

      final def markAsBlocking(): Unit = synchronized {
        if (blocking) ()
        else {
          blocking = true
          val idx = workers.indexOf(self)
          if (idx >= 0) {
            drain(self)
            val worker = cache.poll()
            if (worker eq null) {
              val worker = makeWorker()
              worker.setName(idx)
              worker.setDaemon(true)
              workers(idx) = worker
              worker.start()
            } else {
              worker.setName(idx)
              worker.blocking = false
              worker.active = true
              workers(idx) = worker
              LockSupport.unpark(worker)
            }
          }
        }
      }
    }

  private[this] def submitBlocking(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
    Blocking.blockingExecutor.submit(runnable)
}

private object NIOScheduler {
  private val poolSize = java.lang.Runtime.getRuntime.availableProcessors

  /**
   * If the current thread is a [[NIOScheduler.Worker]] then it is returned,
   * otherwise returns null.
   */
  private def workerOrNull(): NIOScheduler.Worker =
    Thread.currentThread() match {
      case w: NIOScheduler.Worker => w
      case _                      => null
    }

  /**
   * `Locations` tracks the number of observations of a fiber forked from a
   * location.
   */
  private sealed abstract class Locations {
    def get(trace: Trace): Long
    def put(trace: Trace): Long
  }

  private object Locations {

    final class Enabled(sizeHint: Int = 64) extends Locations {
      private[this] val locations = mutable.HashMap.empty[Trace, AtomicLong]
      locations.sizeHint(sizeHint)

      def get(trace: Trace): Long = {
        val v = locations.getOrElse(trace, null)
        if (v eq null) 0L else v.get()
      }

      def put(trace: Trace): Long =
        locations.getOrElseUpdate(trace, new AtomicLong(0L)).getAndIncrement()
    }

    object Disabled extends Locations {
      def get(trace: Trace): Long = 0L
      def put(trace: Trace): Long = 0L
    }
  }

  private sealed abstract class Supervisor extends Thread

  private sealed abstract class Worker extends Thread with BlockContext {

    val submittedLocations: Locations

    @volatile
    var active: Boolean =
      true

    @volatile
    var blocking: Boolean =
      false

    @volatile
    var currentRunnable: Runnable =
      null

    val pendingCount: AtomicInteger =
      new AtomicInteger(0)

    val queue: ConcurrentLinkedQueue[Runnable] =
      new ConcurrentLinkedQueue[Runnable]()

    @volatile
    var nextRunnable: Runnable =
      null

    @volatile
    var opCount: Long =
      0L

    def markAsBlocking(): Unit

    def pending(): Int = {
      val n = pendingCount.get()
      if (nextRunnable eq null) n else n + 1
    }

    final def setName(i: Int): Unit =
      setName(s"NIOScheduler-Worker-$i")

    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      markAsBlocking()
      thunk
    }
  }
}
