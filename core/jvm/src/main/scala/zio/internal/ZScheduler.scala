/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/**
 * A `ZScheduler` is an `Executor` that is optimized for running ZIO
 * applications. Inspired by "Making the Tokio Scheduler 10X Faster" by Carl
 * Lerche. [[https://tokio.rs/blog/2019-10-scheduler]]
 */
private final class ZScheduler(val yieldOpCount: Int) extends zio.Executor {
  private[this] val poolSize    = Runtime.getRuntime.availableProcessors
  private[this] val globalQueue = MutableConcurrentQueue.unbounded[Runnable]
  private[this] val idle        = MutableConcurrentQueue.bounded[ZScheduler.Worker](poolSize)
  private[this] val state       = new AtomicInteger(poolSize << 16)
  private[this] val workers     = Array.ofDim[ZScheduler.Worker](poolSize)

  (0 until poolSize).foreach { workerId =>
    val worker = new ZScheduler.Worker { self =>
      override def run(): Unit = {
        var currentOpCount = 0L
        val random         = ThreadLocalRandom.current
        var runnable       = null.asInstanceOf[Runnable]
        var searching      = false
        while (!isInterrupted) {
          if ((currentOpCount & 63) == 0) {
            runnable = globalQueue.poll(null)
            if (runnable eq null) {
              if (nextRunnable ne null) {
                runnable = nextRunnable
                nextRunnable = null
              } else {
                runnable = localQueue.poll(null)
              }
            }
          } else {
            if (nextRunnable ne null) {
              runnable = nextRunnable
              nextRunnable = null
            } else {
              runnable = localQueue.poll(null)
              if (runnable eq null) {
                runnable = globalQueue.poll(null)
              }
            }
          }
          if (runnable eq null) {
            if (!searching) {
              val currentState  = state.get
              val currentActive = currentState & 0xffff
              if (2 * currentActive < poolSize) {
                state.getAndIncrement()
                searching = true
              }
            }
            if (searching) {
              var i      = 0
              var loop   = true
              val offset = random.nextInt(poolSize)
              while (i != poolSize && loop) {
                val index = (i + offset) % poolSize
                if (index != workerId) {
                  val worker = workers(index)
                  val size   = worker.localQueue.size()
                  if (size > 0) {
                    val runnables = worker.localQueue.pollUpTo(size - size / 2)
                    if (runnables.nonEmpty) {
                      runnable = runnables.head
                      if (runnables.tail.nonEmpty) {
                        localQueue.offerAll(runnables.tail)
                      }
                      loop = false
                    }
                  }
                }
                i += 1
              }
              if (runnable eq null) {
                runnable = globalQueue.poll(null)
              }
            }
          }
          if (runnable eq null) {
            val currentState     = if (searching) state.addAndGet(0xfffeffff) else state.addAndGet(0xffff0000)
            val currentSearching = currentState & 0xffff
            active = false
            idle.offer(self)
            if (currentSearching == 0 && searching) {
              var i      = 0
              var notify = false
              while (i != poolSize && !notify) {
                val worker = workers(i)
                notify = !worker.localQueue.isEmpty()
                i += 1
              }
              if (!notify) {
                notify = !globalQueue.isEmpty()
              }
              if (notify) {
                val currentState     = state.get
                val currentActive    = (currentState & 0xffff0000) >> 16
                val currentSearching = currentState & 0xffff
                if (currentActive != poolSize && currentSearching == 0) {
                  val worker = idle.poll(null)
                  if (worker ne null) {
                    state.getAndAdd(0x10001)
                    worker.active = true
                    LockSupport.unpark(worker)
                  }
                }
              }
            }
            while (!active && !isInterrupted)
              LockSupport.park()
            searching = true
          } else {
            if (searching) {
              searching = false
              val currentState     = state.decrementAndGet()
              val currentSearching = currentState & 0xffff
              val currentActive    = (currentState & 0xffff0000) >> 16
              if (currentActive != poolSize && currentSearching == 0) {
                val worker = idle.poll(null)
                if (worker ne null) {
                  state.getAndAdd(0x10001)
                  worker.active = true
                  LockSupport.unpark(worker)
                }
              }
            }
            runnable.run()
            runnable = null
            currentOpCount += 1
            opCount = currentOpCount
          }
        }
      }
    }
    worker.setName(s"ZScheduler-$workerId")
    worker.setDaemon(true)
    workers(workerId) = worker
  }
  workers.foreach(_.start())

  def unsafeMetrics: Option[ExecutionMetrics] = {
    val metrics = new ExecutionMetrics {
      def capacity: Int =
        Int.MaxValue
      def concurrency: Int =
        poolSize
      def dequeuedCount: Long = {
        var dequeued = 0L
        var i        = 0
        while (i != poolSize) {
          val worker = workers(i)
          dequeued += worker.opCount
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
          enqueued += worker.localQueue.size()
          if (worker.nextRunnable ne null) enqueued += 1
          i += 1
        }
        enqueued += globalQueue.size()
        enqueued
      }
      def size: Int = {
        var i    = 0
        var size = 0
        while (i != poolSize) {
          val worker = workers(i)
          size += worker.localQueue.size()
          if (worker.nextRunnable ne null) size += 1
          i += 1
        }
        size += globalQueue.size()
        size
      }
      def workersCount: Int = {
        val currentState = state.get
        (currentState & 0xffff0000) >> 16
      }
    }
    Some(metrics)
  }

  def unsafeSubmit(runnable: Runnable): Boolean = {
    val currentThread = Thread.currentThread
    if (currentThread.isInstanceOf[ZScheduler.Worker]) {
      val worker = currentThread.asInstanceOf[ZScheduler.Worker]
      if (!worker.localQueue.offer(runnable)) {
        globalQueue.offerAll(worker.localQueue.pollUpTo(128) :+ runnable)
      }
    } else {
      globalQueue.offer(runnable)
    }
    val currentState     = state.get
    val currentActive    = (currentState & 0xffff0000) >> 16
    val currentSearching = currentState & 0xffff
    if (currentActive != poolSize && currentSearching == 0) {
      val worker = idle.poll(null)
      if (worker ne null) {
        state.getAndAdd(0x10001)
        worker.active = true
        LockSupport.unpark(worker)
      }
    }
    true
  }

  override def unsafeSubmitAndYield(runnable: Runnable): Boolean = {
    val currentThread = Thread.currentThread
    var notify        = false
    if (currentThread.isInstanceOf[ZScheduler.Worker]) {
      val worker = currentThread.asInstanceOf[ZScheduler.Worker]
      if ((worker.nextRunnable eq null) && worker.localQueue.isEmpty()) {
        worker.nextRunnable = runnable
      } else {
        if (!worker.localQueue.offer(runnable)) {
          globalQueue.offerAll(worker.localQueue.pollUpTo(128) :+ runnable)
        }
        notify = true
      }
    } else {
      globalQueue.offer(runnable)
      notify = true
    }
    if (notify) {
      val currentState     = state.get
      val currentActive    = (currentState & 0xffff0000) >> 16
      val currentSearching = currentState & 0xffff
      if (currentActive != poolSize && currentSearching == 0) {
        val worker = idle.poll(null)
        if (worker ne null) {
          state.getAndAdd(0x10001)
          worker.active = true
          LockSupport.unpark(worker)
        }
      }
    }
    true
  }
}

object ZScheduler {

  /**
   * A `Worker` is a `Thread` that is responsible for executing actions
   * submitted to the scheduler.
   */
  private sealed abstract class Worker extends Thread {

    /**
     * Whether this worker is currently active.
     */
    @volatile
    var active: Boolean =
      true

    /**
     * The local work queue for this worker.
     */
    val localQueue: MutableConcurrentQueue[Runnable] =
      MutableConcurrentQueue.bounded[Runnable](256)

    /**
     * An optional field providing fast access to the next task to be executed
     * by this worker.
     */
    var nextRunnable: Runnable =
      null

    /**
     * The number of tasks that have been executed by this worker.
     */
    @volatile
    var opCount: Long =
      0L
  }
}
