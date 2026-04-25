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
 * An `NIOScheduler` is an `Executor` that uses a least-loaded scheduling
 * strategy inspired by the NIO runtime.
 * [[https://nurmohammed840.github.io/posts/announcing-nio/]]
 *
 * Instead of work-stealing, new tasks are assigned to the worker with the
 * fewest queued tasks. This avoids contention from cross-thread stealing and
 * reduces bookkeeping overhead while still achieving good load balance.
 */
private final class NIOScheduler(autoBlocking: Boolean) extends Executor { parent =>

  import Trace.{empty => emptyTrace}
  import NIOScheduler.poolSize

  private[this] val cache           = new ConcurrentLinkedQueue[NIOScheduler.Worker]()
  private[this] val globalLocations = makeLocations()
  private[this] val state           = new AtomicInteger(poolSize << 16)
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
      def enqueuedCount: Long = {
        var enqueued = 0L
        var i        = 0
        while (i != poolSize) {
          val worker = workers(i)
          enqueued += worker.opCount
          enqueued += worker.queueLength.get()
          i += 1
        }
        enqueued
      }
      def size: Int = {
        var i    = 0
        var size = 0
        while (i != poolSize) {
          size += workers(i).queueLength.get()
          i += 1
        }
        size
      }
      def workersCount: Int = {
        val currentState = state.get
        (currentState & 0xffff0000) >> 16
      }
    }
    Some(metrics)
  }

  override def stealWork(depth: Int): Boolean = {
    val worker = currentWorkerOrNull()
    if (worker ne null) {
      val runnable = worker.queue.poll()
      if (runnable ne null) {
        worker.queueLength.decrementAndGet()
        if (runnable.isInstanceOf[FiberRunnable]) {
          val fiberRunnable = runnable.asInstanceOf[FiberRunnable]
          worker.currentRunnable = fiberRunnable
          fiberRunnable.run(depth)
        } else {
          runnable.run()
        }
        true
      } else {
        false
      }
    } else {
      false
    }
  }

  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = currentWorkerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      val target = leastLoadedWorker()
      target.enqueue(runnable)
      if (!target.active) {
        target.active = true
        LockSupport.unpark(target)
      }
      true
    }
  }

  override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = currentWorkerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      if ((worker ne null) && !worker.blocking && worker.queueLength.get() == 0) {
        worker.enqueue(runnable)
      } else {
        val target = leastLoadedWorker()
        target.enqueue(runnable)
        if (!target.active) {
          target.active = true
          LockSupport.unpark(target)
        }
      }
      true
    }
  }

  private def leastLoadedWorker(): NIOScheduler.Worker = {
    var minWorker = workers(0)
    var minLoad   = minWorker.queueLength.get()
    var i         = 1
    while (i < poolSize) {
      val w    = workers(i)
      val load = w.queueLength.get()
      if (load < minLoad && !w.blocking) {
        minWorker = w
        minLoad = load
      }
      i += 1
    }
    minWorker
  }

  private def currentWorkerOrNull(): NIOScheduler.Worker =
    Thread.currentThread() match {
      case w: NIOScheduler.Worker => w
      case _                      => null
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
        val workers  = parent.workers
        val cache    = parent.cache
        val poolSize = NIOScheduler.poolSize

        var currentBlocking = false
        var currentOpCount  = 0L
        var runnable        = null.asInstanceOf[Runnable]

        while (!isInterrupted) {
          currentBlocking = blocking

          if (!currentBlocking) {
            runnable = queue.poll()
            if (runnable ne null) {
              queueLength.decrementAndGet()
            }
          }

          if (runnable ne null) {
            currentRunnable = runnable
            runnable.run()
            runnable = null
            currentRunnable = null
            currentOpCount += 1
            opCount = currentOpCount
          } else {
            active = false
            if (currentBlocking) {
              cache.offer(self)
            }
            while (!active && !isInterrupted) {
              LockSupport.park()
            }
          }
        }
      }

      final def markAsBlocking(): Unit = synchronized {
        if (blocking) ()
        else {
          blocking = true
          val idx = workers.indexOf(self)
          if (idx >= 0) {
            val worker = cache.poll()
            if (worker eq null) {
              val worker = makeWorker()
              worker.setName(idx)
              worker.setDaemon(true)
              workers(idx) = worker
              worker.start()
            } else {
              worker.setName(idx)
              workers(idx) = worker
              worker.blocking = false
              worker.active = true
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

    val queue: ConcurrentLinkedQueue[Runnable] =
      new ConcurrentLinkedQueue[Runnable]()

    val queueLength: AtomicInteger =
      new AtomicInteger(0)

    @volatile
    var active: Boolean =
      true

    @volatile
    var blocking: Boolean =
      false

    @volatile
    var currentRunnable: Runnable =
      null

    @volatile
    var opCount: Long =
      0L

    def enqueue(runnable: Runnable): Unit = {
      queue.offer(runnable)
      queueLength.incrementAndGet()
    }

    def markAsBlocking(): Unit

    final def setName(i: Int): Unit =
      setName(s"NIOScheduler-Worker-$i")

    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      markAsBlocking()
      thunk
    }
  }
}
