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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport
import scala.annotation.tailrec
import scala.concurrent.{BlockContext, CanAwait}

/**
 * A scheduler based on a least-loaded algorithm. Tasks are assigned to the
 * worker with the smallest observed load and delivered through a simple
 * multi-producer / single-consumer mailbox.
 */
private final class NioScheduler(autoBlocking: Boolean) extends Executor { parent =>

  import NioScheduler.{poolSize, workerOrNull}

  private[this] val chooser        = new AtomicInteger(0)
  private[this] val completedCount = new AtomicLong(0L)
  private[this] val liveWorkers    = new AtomicInteger(0)
  private[this] val submittedCount = new AtomicLong(0L)
  private[this] val workers        = new AtomicReferenceArray[NioScheduler.Worker](poolSize)

  {
    var i = 0
    while (i < poolSize) {
      val worker = makeWorker(i)
      workers.set(i, worker)
      startWorker(worker)
      i += 1
    }
  }

  override private[zio] def isCurrentThreadInExecutor: Boolean =
    workerOrNull() ne null

  override def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] =
    Some(
      new ExecutionMetrics {
        def capacity: Int =
          Int.MaxValue

        def concurrency: Int =
          poolSize

        def dequeuedCount: Long =
          completedCount.get()

        def enqueuedCount: Long =
          submittedCount.get()

        def size: Int = {
          var i    = 0
          var size = 0
          while (i < poolSize) {
            val worker = workers.get(i)
            val load   = worker.load.get()
            size += (if (worker.running.get()) Math.max(0, load - 1) else load)
            i += 1
          }
          size
        }

        def workersCount: Int =
          liveWorkers.get()
      }
    )

  override def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    submittedCount.incrementAndGet()
    enqueue(runnable)
    true
  }

  private[this] def enqueue(runnable: Runnable): Unit = {
    val worker = reserveLeastLoadedWorker()
    worker.submit(runnable, transfer)
  }

  private[this] def transfer(runnable: Runnable): Unit = {
    val worker = reserveLeastLoadedWorker()
    worker.submit(runnable, transfer)
  }

  @tailrec
  private[this] def reserveLeastLoadedWorker(): NioScheduler.Worker = {
    val start = Math.floorMod(chooser.getAndIncrement(), poolSize)

    var best     = workers.get(start)
    var bestLoad = best.load.get()
    var i        = 1

    while (i < poolSize && bestLoad != 0) {
      val candidate = workers.get((start + i) % poolSize)
      val load      = candidate.load.get()
      if (load < bestLoad) {
        best = candidate
        bestLoad = load
      }
      i += 1
    }

    if (best.load.compareAndSet(bestLoad, bestLoad + 1)) best
    else reserveLeastLoadedWorker()
  }

  private[this] def replaceWorker(worker: NioScheduler.Worker): Unit = {
    val replacement = makeWorker(worker.index)
    if (workers.compareAndSet(worker.index, worker, replacement)) {
      startWorker(replacement)
      worker.drainTo(transfer)
    }
  }

  private[this] def startWorker(worker: NioScheduler.Worker): Unit = {
    liveWorkers.incrementAndGet()
    worker.setDaemon(true)
    worker.setName(worker.index)
    worker.start()
  }

  private[this] def makeWorker(index: Int): NioScheduler.Worker =
    new NioScheduler.Worker(index, autoBlocking) {
      override def markAsBlocking(): Unit =
        if (replaced.compareAndSet(false, true)) {
          parent.replaceWorker(this)
        }

      override def run(): Unit =
        try {
          var continue = true
          while (continue && !isInterrupted) {
            var runnable = inbox.poll()

            if (runnable eq null) {
              if (replaced.get()) {
                drainTo(parent.transfer)
                continue = false
              } else {
                while ((runnable eq null) && !isInterrupted && !replaced.get()) {
                  LockSupport.park(this)
                  runnable = inbox.poll()
                }
                if ((runnable eq null) && replaced.get()) {
                  drainTo(parent.transfer)
                  continue = false
                }
              }
            }

            if (runnable ne null) {
              running.set(true)
              currentRunnable = runnable
              try runnable.run()
              catch {
                case throwable: Throwable => throwable.printStackTrace()
              } finally {
                currentRunnable = null
                running.set(false)
                load.decrementAndGet()
                opCount += 1L
                completedCount.incrementAndGet()
              }
              if (replaced.get()) {
                drainTo(parent.transfer)
                continue = false
              }
            }
          }
        } finally {
          liveWorkers.decrementAndGet()
        }
    }
}

private object NioScheduler {
  private val poolSize = java.lang.Runtime.getRuntime.availableProcessors

  def markCurrentWorkerAsBlocking(): Unit = {
    val worker = workerOrNull()
    if (worker ne null) worker.markAsBlocking()
    else ()
  }

  private def workerOrNull(): NioScheduler.Worker =
    Thread.currentThread() match {
      case worker: NioScheduler.Worker => worker
      case _                           => null
    }

  private sealed abstract class Worker(val index: Int, autoBlocking: Boolean) extends Thread with BlockContext {
    val inbox: ConcurrentLinkedQueue[Runnable] = new ConcurrentLinkedQueue[Runnable]()
    val load: AtomicInteger                    = new AtomicInteger(0)
    val replaced: AtomicBoolean                = new AtomicBoolean(false)
    val running: AtomicBoolean                 = new AtomicBoolean(false)

    @volatile
    var currentRunnable: Runnable =
      null

    @volatile
    var opCount: Long =
      0L

    def markAsBlocking(): Unit

    final def setName(i: Int): Unit =
      setName(s"NioScheduler-Worker-$i")

    final def drainTo(f: Runnable => Unit): Unit = {
      var runnable = inbox.poll()
      while (runnable ne null) {
        load.decrementAndGet()
        f(runnable)
        runnable = inbox.poll()
      }
    }

    final def submit(runnable: Runnable, transfer: Runnable => Unit): Unit = {
      inbox.offer(runnable)
      if (replaced.get() && inbox.remove(runnable)) {
        load.decrementAndGet()
        transfer(runnable)
      } else {
        LockSupport.unpark(this)
      }
    }

    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      if (autoBlocking) markAsBlocking()
      thunk
    }
  }
}
