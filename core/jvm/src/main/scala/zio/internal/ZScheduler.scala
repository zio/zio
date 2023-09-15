/*
 * Copyright 2021-2023 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/**
 * A `ZScheduler` is an `Executor` that is optimized for running ZIO
 * applications. Inspired by "Making the Tokio Scheduler 10X Faster" by Carl
 * Lerche. [[https://tokio.rs/blog/2019-10-scheduler]]
 */
private final class ZScheduler(autoBlocking: Boolean) extends Executor {
  private[this] val poolSize           = java.lang.Runtime.getRuntime.availableProcessors
  private[this] val cache              = MutableConcurrentQueue.unbounded[ZScheduler.Worker]
  private[this] val globalQueue        = MutableConcurrentQueue.unbounded[Runnable]
  private[this] val idle               = MutableConcurrentQueue.bounded[ZScheduler.Worker](poolSize)
  private[this] val state              = new AtomicInteger(poolSize << 16)
  private[this] val submittedLocations = makeLocations()
  private[this] val workers            = Array.ofDim[ZScheduler.Worker](poolSize)

  @volatile private[this] var blockingLocations: Set[Trace] = Set.empty

  (0 until poolSize).foreach { workerId =>
    val worker = makeWorker()
    worker.setName(s"ZScheduler-Worker-$workerId")
    worker.setDaemon(true)
    workers(workerId) = worker
  }
  workers.foreach(_.start())

  if (autoBlocking) {
    val supervisor = makeSupervisor()
    supervisor.setName("ZScheduler-Supervisor")
    supervisor.setDaemon(true)
    supervisor.start()
  }

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

  override def stealWork(depth: Int)(implicit unsafe: Unsafe): Boolean = {
    val currentThread = Thread.currentThread
    if (currentThread.isInstanceOf[ZScheduler.Worker]) {
      val worker   = currentThread.asInstanceOf[ZScheduler.Worker]
      var runnable = null.asInstanceOf[Runnable]
      if (worker.nextRunnable ne null) {
        runnable = worker.nextRunnable
        worker.nextRunnable = null
      } else {
        runnable = worker.localQueue.poll(null)
        if (runnable eq null) {
          runnable = globalQueue.poll(null)
        }
      }

      if (runnable ne null) {
        if (runnable.isInstanceOf[FiberRunnable]) {
          val fiberRunnable = runnable.asInstanceOf[FiberRunnable]
          worker.currentRunnable = fiberRunnable
          fiberRunnable.run(depth)
        } else {
          runnable.run()
        }
        true
      } else {
        worker.nextRunnable = runnable
        false
      }
    } else {
      false
    }
  }

  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
    if (isBlocking(runnable)) {
      submitBlocking(runnable)
    } else {
      val currentThread = Thread.currentThread
      if (currentThread.isInstanceOf[ZScheduler.Worker]) {
        val worker = currentThread.asInstanceOf[ZScheduler.Worker]
        if (worker.blocking) {
          globalQueue.offer(runnable)
        } else if (worker.localQueue.offer(runnable)) {
          if (worker.blocking) {
            val runnable = worker.localQueue.poll(null)
            if (runnable ne null) {
              globalQueue.offer(runnable)
            }
          }
        } else {
          globalQueue.offerAll(worker.localQueue.pollUpTo(128) :+ runnable)
        }
      } else {
        globalQueue.offer(runnable)
      }
      val currentState     = state.get
      val currentActive    = (currentState & 0xffff0000) >> 16
      val currentSearching = currentState & 0xffff
      if (currentActive != poolSize && currentSearching == 0) {
        var loop = true
        while (loop) {
          val worker = idle.poll(null)
          if (worker eq null) {
            loop = false
          } else {
            state.getAndAdd(0x10001)
            worker.active = true
            LockSupport.unpark(worker)
            loop = false
          }
        }
      }
      true
    }

  override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
    if (isBlocking(runnable)) {
      submitBlocking(runnable)
    } else {
      val currentThread = Thread.currentThread
      var notify        = false
      if (currentThread.isInstanceOf[ZScheduler.Worker]) {
        val worker = currentThread.asInstanceOf[ZScheduler.Worker]
        if (worker.blocking) {
          globalQueue.offer(runnable)
          notify = true
        } else if ((worker.nextRunnable eq null) && worker.localQueue.isEmpty()) {
          worker.nextRunnable = runnable
        } else if (worker.localQueue.offer(runnable)) {
          if (worker.blocking) {
            val runnable = worker.localQueue.poll(null)
            if (runnable ne null) {
              globalQueue.offer(runnable)
            }
          }
          notify = true
        } else {
          globalQueue.offerAll(worker.localQueue.pollUpTo(128) :+ runnable)
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
          var loop = true
          while (loop) {
            val worker = idle.poll(null)
            if (worker eq null) {
              loop = false
            } else {
              state.getAndAdd(0x10001)
              worker.active = true
              LockSupport.unpark(worker)
              loop = false
            }
          }
        }
      }
      true
    }

  private[this] def isBlocking(runnable: Runnable): Boolean =
    if (runnable.isInstanceOf[FiberRunnable]) {
      val fiberRunnable = runnable.asInstanceOf[FiberRunnable]
      val location      = fiberRunnable.location
      submittedLocations.put(location)
      blockingLocations.contains(location)
    } else {
      false
    }

  private[this] def makeLocations(): ZScheduler.Locations =
    new ZScheduler.Locations {
      private[this] val locations = new java.util.HashMap[Trace, Array[Long]]
      def get(trace: Trace): Long = {
        val array = locations.get(trace)
        if (array eq null) 0L else array(0)
      }
      def put(trace: Trace): Long = {
        val array = locations.get(trace)
        if (array eq null) {
          locations.put(trace, Array(1L))
          0L
        } else {
          val value = array(0)
          array(0) += 1
          value
        }
      }
    }

  private[this] def makeSupervisor(): ZScheduler.Supervisor =
    new ZScheduler.Supervisor {
      override def run(): Unit = {
        var currentTime         = java.lang.System.currentTimeMillis()
        val identifiedLocations = makeLocations()
        val previousOpCounts    = Array.fill(poolSize)(-1L)
        while (!isInterrupted) {
          var workerId = 0
          while (workerId != poolSize) {
            val currentWorker = workers(workerId)
            if (currentWorker.active) {
              val currentOpCount  = currentWorker.opCount
              val previousOpCount = previousOpCounts(workerId)
              if (currentOpCount == previousOpCount) {
                val currentRunnable = currentWorker.currentRunnable
                if (currentRunnable.isInstanceOf[FiberRunnable]) {
                  val fiberRunnable = currentRunnable.asInstanceOf[FiberRunnable]
                  val location      = fiberRunnable.location
                  if (location ne Trace.empty) {
                    val identifiedCount = identifiedLocations.put(location)
                    val submittedCount  = submittedLocations.get(location)
                    if (submittedCount > 64 && identifiedCount >= submittedCount / 2) {
                      blockingLocations += location
                    }
                  }
                }
                previousOpCounts(workerId) = -1L
                currentWorker.blocking = true
                val runnables = currentWorker.localQueue.pollUpTo(256)
                globalQueue.offerAll(runnables)
                val worker = cache.poll(null)
                if (worker eq null) {
                  val worker = makeWorker()
                  worker.setName(s"ZScheduler-Worker-$workerId")
                  worker.setDaemon(true)
                  workers(workerId) = worker
                  worker.start()
                } else {
                  state.getAndIncrement()
                  worker.setName(s"ZScheduler-Worker-$workerId")
                  workers(workerId) = worker
                  worker.blocking = false
                  worker.active = true
                  LockSupport.unpark(worker)
                }
              } else {
                previousOpCounts(workerId) = currentOpCount
              }
            } else {
              previousOpCounts(workerId) = -1L
            }
            workerId += 1
          }
          val deadline = currentTime + 100
          var loop     = true
          while (loop) {
            LockSupport.parkUntil(deadline)
            currentTime = java.lang.System.currentTimeMillis()
            loop = currentTime < deadline
          }
          Fiber._roots.graduate()
        }
      }
    }

  private[this] def makeWorker(): ZScheduler.Worker =
    new ZScheduler.Worker { self =>
      override def run(): Unit = {
        var currentBlocking = false
        var currentOpCount  = 0L
        val random          = ThreadLocalRandom.current
        var runnable        = null.asInstanceOf[Runnable]
        var searching       = false
        while (!isInterrupted) {
          currentBlocking = blocking
          if (currentBlocking) {
            if (nextRunnable ne null) {
              runnable = nextRunnable
              nextRunnable = null
            }
          } else {
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
                  val index  = (i + offset) % poolSize
                  val worker = workers(index)
                  if ((worker ne self) && !worker.blocking) {
                    val size = worker.localQueue.size()
                    if (size > 0) {
                      val runnables = worker.localQueue.pollUpTo(size - size / 2)
                      if (runnables.nonEmpty) {
                        runnable = runnables.head
                        if (runnables.tail.nonEmpty) {
                          localQueue.offerAll(runnables.tail)
                        }
                        currentBlocking = blocking
                        if (currentBlocking) {
                          val runnables = localQueue.pollUpTo(256)
                          if (runnables.nonEmpty) {
                            globalQueue.offerAll(runnables)
                          }
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
          }
          if (runnable eq null) {
            val currentState =
              if (currentBlocking && searching) state.decrementAndGet()
              else if (currentBlocking) state.get
              else if (searching) state.addAndGet(0xfffeffff)
              else state.addAndGet(0xffff0000)
            val currentSearching = currentState & 0xffff
            active = false
            if (currentBlocking) {
              cache.offer(self)
            } else {
              idle.offer(self)
            }
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
                  var loop = true
                  while (loop) {
                    val worker = idle.poll(null)
                    if (worker eq null) {
                      loop = false
                    } else {
                      state.getAndAdd(0x10001)
                      worker.active = true
                      LockSupport.unpark(worker)
                      loop = false
                    }
                  }
                }
              }
            }
            while (!active && !isInterrupted) {
              LockSupport.park()
            }
            searching = true
          } else {
            if (searching) {
              searching = false
              val currentState     = state.decrementAndGet()
              val currentSearching = currentState & 0xffff
              val currentActive    = (currentState & 0xffff0000) >> 16
              if (currentActive != poolSize && currentSearching == 0) {
                var loop = true
                while (loop) {
                  val worker = idle.poll(null)
                  if (worker eq null) {
                    loop = false
                  } else {
                    state.getAndAdd(0x10001)
                    worker.active = true
                    LockSupport.unpark(worker)
                    loop = false
                  }
                }
              }
            }
            currentRunnable = runnable
            runnable.run()
            runnable = null
            currentRunnable = runnable
            currentOpCount += 1
            opCount = currentOpCount
          }
        }
      }
    }

  private[this] def submitBlocking(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
    Blocking.blockingExecutor.submit(runnable)
}

private[zio] object ZScheduler {

  /**
   * `Locations` tracks the number of observations of a fiber forked from a
   * location.
   */
  private sealed abstract class Locations {

    /**
     * Returns the number of observations of a fiber forked from the specified
     * location.
     */
    def get(trace: Trace): Long

    /**
     * Tracks a new observation of a fiber forked from the specified location
     * and returns the previous number of observations of a fiber forked from
     * that location.
     */
    def put(trace: Trace): Long
  }

  /**
   * A `Supervisor` is a `Thread` that is responsible for monitoring workers and
   * shifting tasks from workers that are blocking to new workers.
   */
  private sealed abstract class Supervisor extends Thread

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
     * Whether this worker is currently blocking.
     */
    @volatile
    var blocking: Boolean =
      false

    /**
     * The current task being executed by this worker.
     */
    @volatile
    var currentRunnable: Runnable =
      null

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
