/*
 * Copyright 2024-2024 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.BlockContext
import scala.concurrent.CanAwait

/**
 * A `NioScheduler` is an [[Executor]] that uses a '''Least-Loaded scheduling'''
 * algorithm.
 *
 * Unlike the work-stealing scheduler ([[ZScheduler]]), this scheduler assigns
 * new tasks to the worker with the least workload. This approach:
 *   - Eliminates the complexity of work-stealing
 *   - Reduces contention on shared queues
 *   - Provides natural load balancing
 *   - Is simpler to implement and maintain
 *
 * ==Threading Model==
 *
 * The scheduler creates a pool of daemon worker threads (one per available
 * processor). Each worker has a local [[RingBufferPow2]] queue (capacity 256).
 * Tasks that overflow local queues spill to a global [[ConcurrentLinkedQueue]].
 *
 * When a worker runs out of local and global work, it enters ''searching'' mode
 * and attempts to steal half of another worker's tasks. If no work is found,
 * the worker parks until signaled or a 10ms safety-net timeout expires.
 *
 * ==Auto-Blocking==
 *
 * When `autoBlocking` is enabled, a supervisor thread monitors workers every
 * 100ms. Workers whose operation count hasn't changed are marked as blocking. A
 * blocked worker's remaining tasks migrate to the global queue, and a fresh
 * replacement worker is spawned in its place.
 *
 * ==State Encoding==
 *
 * An [[AtomicInteger]] tracks two 16-bit counters packed into one word:
 *   - Bits 16–31: count of ''active'' workers
 *   - Bits 0–15 : count of ''searching'' workers
 *
 * ==Usage==
 *
 * {{{
 * // Enable via bootstrap layer
 * override val bootstrap = Runtime.enableNioScheduler
 *
 * // Or create directly
 * val executor = Executor.makeNio()
 * }}}
 *
 * Inspired by the Nio async runtime for Rust.
 * [[https://nurmohammed840.github.io/posts/announcing-nio/]]
 */
private final class NioScheduler(autoBlocking: Boolean) extends Executor { parent =>

  import NioScheduler.poolSize

  private[this] val globalQueue = new ConcurrentLinkedQueue[Runnable]()
  private[this] val workers     = Array.ofDim[NioScheduler.Worker](poolSize)
  private[this] val state       = new AtomicInteger(poolSize << 16)

  @volatile private[this] var _shutdown                          = false
  @volatile private[this] var supervisor: NioScheduler.Supervisor = _

  // Initialize workers
  (0 until poolSize).foreach { workerId =>
    val worker = makeWorker()
    worker.setName(workerId)
    worker.setDaemon(true)
    workers(workerId) = worker
  }
  workers.foreach(_.start())

  if (autoBlocking) {
    supervisor = makeSupervisor()
    supervisor.setName("NioScheduler-Supervisor")
    supervisor.setDaemon(true)
    supervisor.start()
  }

  /** Returns `true` if the current thread is an [[NioScheduler.Worker]]. */
  override private[zio] def isCurrentThreadInExecutor: Boolean =
    Thread.currentThread().isInstanceOf[NioScheduler.Worker]

  /**
   * Returns execution metrics aggregated across all workers and the global
   * queue. Thread-safe: reads volatile fields and lock-free data structures.
   */
  def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] = {
    val metrics = new ExecutionMetrics {
      def capacity: Int = Int.MaxValue

      def concurrency: Int = poolSize

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

  /**
   * Attempts to execute a pending task on the current worker thread. Checks
   * `nextRunnable`, then local queue, then global queue. If the task is a
   * [[FiberRunnable]], runs it with the given depth.
   */
  override def stealWork(depth: Int): Boolean = {
    val worker = currentWorker()
    if (worker ne null) {
      var runnable: Runnable = null

      // Try to get from nextRunnable first
      if (worker.nextRunnable ne null) {
        runnable = worker.nextRunnable
        worker.nextRunnable = null
      } else {
        runnable = worker.localQueue.poll(null)
        if (runnable eq null) {
          runnable = globalQueue.poll()
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

  /**
   * Submits a task for execution. If the current thread is a non-blocking
   * worker, enqueues to its local queue (overflowing to global). Otherwise,
   * routes to the least-loaded worker via [[submitToLeastLoaded]].
   */
  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    if (_shutdown) return false

    val worker = currentWorker()

    // If we're on a worker thread and not blocking, try to submit locally
    if ((worker ne null) && !worker.blocking) {
      if (!worker.localQueue.offer(runnable)) {
        // Local queue is full, spill to global queue
        globalQueue.offer(runnable)
      }
    } else {
      // Submit to least-loaded worker or global queue
      submitToLeastLoaded(runnable)
    }

    maybeUnparkWorker()
    true
  }

  /**
   * Submits a task and signals that the current fiber is willing to yield. On a
   * non-blocking worker with empty queues, the task is placed directly into
   * `nextRunnable` for immediate execution (bypassing the queue).
   */
  override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    if (_shutdown) return false

    val worker = currentWorker()

    if ((worker ne null) && !worker.blocking) {
      // Try to resume on current thread if queues are empty
      if ((worker.nextRunnable eq null) && worker.localQueue.isEmpty()) {
        val fromGlobal = globalQueue.poll()
        if (fromGlobal eq null) {
          // Happy path - can run immediately
          worker.nextRunnable = runnable
          return true
        } else {
          // Global queue has work, prioritize it
          worker.nextRunnable = fromGlobal
          worker.localQueue.offer(runnable)
        }
      } else if (!worker.localQueue.offer(runnable)) {
        globalQueue.offer(runnable)
      }
    } else {
      submitToLeastLoaded(runnable)
    }

    maybeUnparkWorker()
    true
  }

  /**
   * Submits a runnable to the worker with the least workload. This is the core
   * of the Least-Loaded scheduling algorithm.
   *
   * Scans all non-blocking workers and selects the one with the smallest local
   * queue. Early-exits if an empty worker is found. Falls back to the global
   * queue when all workers are busy or blocking.
   */
  private def submitToLeastLoaded(runnable: Runnable): Unit = {
    var leastLoadedWorker: NioScheduler.Worker = null
    var minLoad                                = Int.MaxValue
    var found                                  = false

    var i = 0
    while (i < poolSize && !found) {
      val worker = workers(i)
      if (!worker.blocking) {
        val load = worker.localQueue.size()
        if (load < minLoad) {
          minLoad = load
          leastLoadedWorker = worker
          // Early exit if we find an empty worker
          found = load == 0
        }
      }
      i += 1
    }

    if ((leastLoadedWorker ne null) && minLoad < 256) {
      if (!leastLoadedWorker.localQueue.offer(runnable)) {
        globalQueue.offer(runnable)
      }
      // Wake up the specific worker that received the task
      unparkWorker(leastLoadedWorker)
    } else {
      // All workers are busy or blocking, use global queue
      globalQueue.offer(runnable)
    }
  }

  private def currentWorker(): NioScheduler.Worker =
    Thread.currentThread() match {
      case w: NioScheduler.Worker => w
      case _                      => null
    }

  /**
   * Wakes up a specific worker if it is idle.
   */
  private def unparkWorker(worker: NioScheduler.Worker): Unit =
    if (!worker.active && !worker.blocking) {
      state.getAndAdd(0x10001)
      worker.active = true
      LockSupport.unpark(worker)
    }

  private def maybeUnparkWorker(): Unit = {
    val currentState     = state.get
    val currentActive    = (currentState & 0xffff0000) >> 16
    val currentSearching = currentState & 0xffff

    if (currentActive < poolSize && currentSearching == 0) {
      // Find an inactive worker to wake up
      var i = 0
      while (i < poolSize) {
        val worker = workers(i)
        if (!worker.active && !worker.blocking) {
          state.getAndAdd(0x10001)
          worker.active = true
          LockSupport.unpark(worker)
          return
        }
        i += 1
      }
    }
  }

  private def makeSupervisor(): NioScheduler.Supervisor =
    new NioScheduler.Supervisor {
      override def run(): Unit = {
        val previousOpCounts = Array.fill(poolSize)(-1L)
        while (!isInterrupted && !_shutdown) {
          var workerId = 0
          while (workerId < poolSize) {
            val currentWorker = workers(workerId)
            if (currentWorker.active) {
              val currentOpCount  = currentWorker.opCount
              val previousOpCount = previousOpCounts(workerId)
              if (currentOpCount == previousOpCount) {
                currentWorker.markAsBlocking()
              } else {
                previousOpCounts(workerId) = currentOpCount
              }
            } else {
              previousOpCounts(workerId) = -1L
            }
            workerId += 1
          }
          Thread.sleep(100)
        }
      }
    }

  private def makeWorker(): NioScheduler.Worker =
    new NioScheduler.Worker {
      self =>
      final override def run(): Unit = {
        val globalQueue = parent.globalQueue
        val workers     = parent.workers
        val state       = parent.state

        var currentOpCount     = 0L
        var runnable: Runnable = null
        var searching          = false

        while (!isInterrupted && !_shutdown) {
          // Try to get from nextRunnable first
          if (nextRunnable ne null) {
            runnable = nextRunnable
            nextRunnable = null
          } else {
            // Try local queue first
            runnable = localQueue.poll(null)

            // If local queue is empty, try global queue
            if (runnable eq null) {
              runnable = globalQueue.poll()
            }

            // If still empty and not yet searching, become a searching worker
            if ((runnable eq null) && !searching) {
              val currentState     = state.get
              val currentSearching = currentState & 0xffff
              if (2 * currentSearching < poolSize) {
                state.getAndIncrement()
                searching = true
              }
            }

            // If we're searching and still no work, try other workers' queues
            if ((runnable eq null) && searching) {
              var i = 0
              while (i < poolSize && (runnable eq null)) {
                val otherWorker = workers(i)
                if ((otherWorker ne self) && !otherWorker.blocking) {
                  val sz = otherWorker.localQueue.size()
                  if (sz > 1) {
                    // Steal half of the tasks
                    val toSteal = sz / 2
                    val stolen  = otherWorker.localQueue.pollUpTo(toSteal)
                    if (!stolen.isEmpty) {
                      val iter = stolen.iterator
                      runnable = iter.next()
                      // Put the rest in our local queue
                      while (iter.hasNext) {
                        localQueue.offer(iter.next())
                      }
                    }
                  }
                }
                i += 1
              }

              // Try global queue again after potential stealing
              if (runnable eq null) {
                runnable = globalQueue.poll()
              }
            }
          }

          if (runnable eq null) {
            // No work found, go idle
            val currentState =
              if (searching) state.addAndGet(0xfffeffff)
              else state.addAndGet(0xffff0000)

            active = false

            if (searching) {
              val currentSearching = currentState & 0xffff
              if (currentSearching == 0) {
                // Check if there's work before parking
                if (!globalQueue.isEmpty || !localQueue.isEmpty()) {
                  maybeUnparkWorker()
                }
              }
            }

            // Park until woken up. Use parkNanos with a timeout as a safety net
            // against missed unparks that could leave tasks stranded.
            var parked = false
            while (!active && !isInterrupted && !_shutdown) {
              // Double-check for work to avoid race condition
              if (!globalQueue.isEmpty || !localQueue.isEmpty()) {
                // Found work, don't park - increment state to become active again
                state.getAndAdd(0x10001)
                active = true
              } else if (!parked) {
                LockSupport.park()
                parked = true
              } else {
                // Safety net: after a park, if we still have no work and no wake-up,
                // use a timed park to periodically re-check
                LockSupport.parkNanos(10_000_000L) // 10ms
              }
            }

            searching = true
          } else {
            // Found work, execute it
            if (searching) {
              searching = false
              state.decrementAndGet()
              maybeUnparkWorker()
            }

            currentRunnable = runnable
            runnable.run()
            runnable = null
            currentRunnable = null
            currentOpCount += 1
            opCount = currentOpCount
          }
        }
      }

      final def markAsBlocking(): Unit = synchronized {
        if (blocking) return

        blocking = true
        val idx = workers.indexOf(self)
        if (idx >= 0) {
          // Move remaining tasks to global queue
          val runnables = localQueue.pollUpTo(256)
          if (nextRunnable ne null) {
            globalQueue.offer(nextRunnable)
            nextRunnable = null
          }
          runnables.foreach(globalQueue.offer)

          // Spawn a replacement worker and increment state to account for it
          state.getAndAdd(0x10001)
          val newWorker = makeWorker()
          newWorker.setName(idx)
          newWorker.setDaemon(true)
          workers(idx) = newWorker
          newWorker.start()
        }
      }
    }

  /**
   * Shuts down the scheduler gracefully.
   */
  def shutdown(): Unit = {
    this._shutdown = true
    if (supervisor ne null) {
      supervisor.interrupt()
    }
    workers.foreach(_.interrupt())
  }
}

private object NioScheduler {
  private val poolSize: Int = java.lang.Runtime.getRuntime.availableProcessors

  /**
   * Marks the current thread as blocking if it is an [[NioScheduler.Worker]].
   * Called by [[Blocking.signalBlocking()]] to handle blocking operations
   * detected at the ZIO runtime level. When a worker is marked blocking, its
   * remaining tasks migrate to the global queue and a replacement worker is
   * spawned.
   */
  def markCurrentWorkerAsBlocking(): Unit =
    Thread.currentThread() match {
      case w: NioScheduler.Worker => w.markAsBlocking()
      case _                      => ()
    }

  /**
   * A supervisor thread that monitors workers for blocking operations.
   * Periodically checks each active worker's `opCount`; if unchanged since the
   * last check, the worker is marked as blocking via
   * [[Worker.markAsBlocking()]].
   *
   * Only created when `autoBlocking = true`.
   */
  private abstract class Supervisor extends Thread

  /**
   * A worker thread that executes tasks submitted to the scheduler.
   *
   * Each worker has:
   *   - A local [[RingBufferPow2]] queue (capacity 256) for fast task access
   *   - A `nextRunnable` field for single-task bypass of the queue
   *   - An `opCount` counter for blocking detection by the supervisor
   *
   * Workers integrate with Scala's [[BlockContext]] so that blocking Scala
   * constructs (e.g., `Await.result`) automatically trigger
   * [[markAsBlocking()]].
   */
  private sealed abstract class Worker extends Thread with BlockContext {

    /**
     * Whether this worker is currently active.
     */
    @volatile
    var active: Boolean = true

    /**
     * Whether this worker is currently blocking.
     */
    @volatile
    var blocking: Boolean = false

    /**
     * The current task being executed by this worker.
     */
    @volatile
    var currentRunnable: Runnable = null

    /**
     * The local work queue for this worker.
     */
    val localQueue: RingBufferPow2[Runnable] =
      RingBufferPow2[Runnable](256)

    /**
     * An optional field for fast access to the next task.
     */
    var nextRunnable: Runnable = null

    /**
     * The number of tasks executed by this worker.
     */
    @volatile
    var opCount: Long = 0L

    /**
     * Marks this worker as blocking, migrates its remaining tasks to the global
     * queue, and spawns a replacement worker in its place.
     */
    def markAsBlocking(): Unit

    final def setName(i: Int): Unit =
      setName(s"NioScheduler-Worker-$i")

    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      markAsBlocking()
      thunk
    }
  }
}
