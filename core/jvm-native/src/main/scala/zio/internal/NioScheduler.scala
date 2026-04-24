/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{Pipe, SelectionKey, Selector}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import java.util.concurrent.{ConcurrentLinkedQueue, ThreadLocalRandom}
import scala.annotation.tailrec

/**
 * A `NioScheduler` is an `Executor` that uses Java NIO Selector for efficient
 * event-driven task scheduling. This scheduler reduces thread park/unpark
 * frequency by using NIO's event loop mechanism.
 *
 * Key features:
 *  - Uses Selector.select() instead of LockSupport.park() for better performance
 *  - Batches task processing to reduce context switching
 *  - Integrates NIO events with ZIO fiber scheduling
 *  - Reduces CPU contention through event-driven architecture
 */
private final class NioScheduler(autoBlocking: Boolean) extends Executor { parent =>

  import NioScheduler.{poolSize, workerOrNull}

  private[this] val globalQueue     = new PartitionedLinkedQueue[Runnable](poolSize * 4)
  private[this] val cache           = new ConcurrentLinkedQueue[NioScheduler.Worker]()
  private[this] val idle            = new ConcurrentLinkedQueue[NioScheduler.Worker]()
  private[this] val globalLocations = makeLocations()
  private[this] val state           = new AtomicInteger(poolSize << 16)
  private[this] val workers         = Array.ofDim[NioScheduler.Worker](poolSize)
  private[this] val selectorPool    = Array.ofDim[Selector](poolSize)
  private[this] val wakerPool       = Array.ofDim[NioScheduler.Waker](poolSize)

  @volatile private[this] var blockingLocations: Set[Trace] = Set.empty
  @volatile private[this] var running: Boolean = true

  // Initialize selector pool and workers
  (0 until poolSize).foreach { workerId =>
    val selector = Selector.open()
    selectorPool(workerId) = selector
    val waker = new NioScheduler.Waker(selector)
    wakerPool(workerId) = waker
    val worker = makeWorker(workerId, selector, waker)
    worker.setName(workerId)
    worker.setDaemon(true)
    workers(workerId) = worker
  }
  workers.foreach(_.start())

  if (autoBlocking) {
    val supervisor = makeSupervisor()
    supervisor.setName("NioScheduler-Supervisor")
    supervisor.setDaemon(true)
    supervisor.start()
  }

  override private[zio] def isCurrentThreadInExecutor: Boolean =
    Thread.currentThread().isInstanceOf[NioScheduler.Worker]

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

  override def stealWork(depth: Int): Boolean = {
    val worker = workerOrNull()
    if (worker ne null) {
      var runnable = null.asInstanceOf[Runnable]
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

  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = workerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      if ((worker eq null) || worker.blocking) {
        globalQueue.offer(runnable)
      } else if (!worker.localQueue.offer(runnable)) {
        handleFullWorkerQueue(worker, runnable)
      } else ()
      val currentState = state.get
      maybeWakeWorker(currentState)
      true
    }
  }

  override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = workerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      var notify = true
      if ((worker eq null) || worker.blocking) {
        globalQueue.offer(runnable)
      }
      else if ((worker.nextRunnable eq null) && worker.localQueue.isEmpty()) {
        val fromGlobal = globalQueue.poll()
        if (fromGlobal eq null) {
          worker.nextRunnable = runnable
          notify = false
        } else {
          worker.nextRunnable = fromGlobal
          worker.localQueue.offer(runnable)
        }
      }
      else if (!worker.localQueue.offer(runnable)) {
        handleFullWorkerQueue(worker, runnable)
      }

      if (notify) {
        val currentState = state.get
        maybeWakeWorker(currentState)
      }
      true
    }
  }

  private def handleFullWorkerQueue(worker: NioScheduler.Worker, runnable: Runnable): Unit = {
    val rnd    = ThreadLocalRandom.current
    val polled = worker.localQueue.pollUpTo(128)
    globalQueue.offerAll(polled, rnd)
    val accepted = worker.localQueue.offer(runnable)
    if (!accepted) {
      globalQueue.offer(runnable, rnd)
    }
  }

  private[this] def isBlocking(worker: NioScheduler.Worker, runnable: Runnable): Boolean =
    if (autoBlocking && runnable.isInstanceOf[FiberRunnable]) {
      val fiberRunnable = runnable.asInstanceOf[FiberRunnable]
      val location      = fiberRunnable.location
      if ((location ne null) && (location ne Trace.empty)) {
        if (worker eq null) globalLocations.put(location)
        else worker.submittedLocations.put(location)
        blockingLocations.contains(location)
      } else false
    } else false

  private[this] def makeLocations(): NioScheduler.Locations =
    if (autoBlocking) new NioScheduler.Locations.Enabled
    else NioScheduler.Locations.Disabled

  private[this] def makeSupervisor(): NioScheduler.Supervisor =
    new NioScheduler.Supervisor {

      private def countSubmittedAt(location: Trace): Long = {
        var count = globalLocations.get(location)
        var i     = 0
        while (i < poolSize) {
          val workerCount = workers(i).submittedLocations.get(location)
          count += workerCount
          i += 1
        }
        count
      }

      override def run(): Unit = {
        val identifiedLocations = makeLocations()
        val previousOpCounts    = Array.fill(poolSize)(-1L)
        while (!isInterrupted && running) {
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
                  if (location ne Trace.empty) {
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
          // Use NIO-aware sleeping instead of LockSupport.parkUntil
          Thread.sleep(100)
        }
      }
    }

  private[this] def makeWorker(workerId: Int, selector: Selector, waker: NioScheduler.Waker): NioScheduler.Worker =
    new NioScheduler.Worker {
      self =>
      override val submittedLocations: NioScheduler.Locations = makeLocations()
      override val workerSelector: Selector = selector
      override val workerWaker: NioScheduler.Waker = waker

      final override def run(): Unit = {
        val globalQueue = parent.globalQueue
        val workers     = parent.workers
        val state       = parent.state
        val cache       = parent.cache
        val idle        = parent.idle
        val poolSize    = NioScheduler.poolSize

        var currentBlocking = false
        var currentOpCount  = 0L
        val random          = ThreadLocalRandom.current
        var runnable        = null.asInstanceOf[Runnable]
        var searching       = false

        while (!isInterrupted && running) {
          currentBlocking = blocking
          val currentNextRunnable = nextRunnable
          if (currentBlocking) ()
          else if (currentNextRunnable ne null) {
            runnable = currentNextRunnable
            nextRunnable = null
          } else {
            // Check local queue first
            runnable = localQueue.poll(null)
            if (runnable eq null) {
              runnable = globalQueue.poll(random)
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
                // Attempt work stealing from other workers
                var i      = 0
                var loop   = true
                val offset = random.nextInt(poolSize)
                while (i != poolSize && loop) {
                  val index  = (i + offset) % poolSize
                  val worker = workers(index)
                  if ((worker ne self) && !worker.blocking) {
                    val size = worker.localQueue.size()
                    if (size > 0) {
                      val runnables  = worker.localQueue.pollUpTo(size - size / 2)
                      val nRunnables = runnables.size
                      if (nRunnables > 0) {
                        val iter = runnables.iterator
                        runnable = iter.next()
                        if (nRunnables > 1) localQueue.offerAll(iter, nRunnables - 1)
                        currentBlocking = blocking
                        if (currentBlocking) {
                          val runnables = localQueue.pollUpTo(256)
                          if (!runnables.isEmpty) {
                            globalQueue.offerAll(runnables, random)
                          }
                        }
                        loop = false
                      }
                    }
                  }
                  i += 1
                }
                if (runnable eq null) {
                  runnable = globalQueue.poll(random)
                }
              }
            }
          }

          if (runnable eq null) {
            // No work available - use NIO selector for efficient waiting
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
                val currentState = state.get
                maybeWakeWorker(currentState)
              }
            }

            // Use NIO selector instead of LockSupport.park()
            waitForWorkOrTimeout()
            searching = true
          } else {
            if (searching) {
              searching = false
              val currentState = state.decrementAndGet()
              maybeWakeWorker(currentState)
            }
            currentRunnable = runnable
            runnable.run()
            runnable = null
            currentRunnable = runnable
            currentOpCount += 1
            opCount = currentOpCount
          }
        }

        // Cleanup
        try {
          selector.close()
        } catch {
          case _: IOException => ()
        }
      }

      /**
       * Uses NIO Selector to wait for work with timeout.
       * More efficient than LockSupport.park() as it allows
       * batched wakeups and integration with NIO events.
       */
      private def waitForWorkOrTimeout(): Unit = {
        if (!active) {
          // Wait for new work or timeout using selector
          val selected = workerSelector.select(10L) // 10ms timeout for responsiveness
          if (selected > 0) {
            // Process any pending wake events
            val keys = workerSelector.selectedKeys()
            val iter = keys.iterator()
            while (iter.hasNext()) {
              iter.next()
              iter.remove()
            }
            keys.clear()
          }
          // Check if we should become active again
          while (!active && !isInterrupted && running) {
            val selected = workerSelector.select(1L)
            if (selected > 0) {
              val keys = workerSelector.selectedKeys()
              keys.clear()
            }
          }
        }
      }

      // NOTE: Synchronized block for supervisor/external calls
      final def markAsBlocking(): Unit = synchronized {
        if (blocking) ()
        else {
          blocking = true
          val idx = workers.indexOf(self)
          if (idx >= 0) {
            val runnables = self.localQueue.pollUpTo(256)
            if (nextRunnable ne null) {
              globalQueue.offer(nextRunnable)
              nextRunnable = null
            }
            globalQueue.offerAll(runnables)
            val worker = cache.poll()
            if (worker eq null) {
              val newSelector = Selector.open()
              val newWaker = new NioScheduler.Waker(newSelector)
              val newWorker = makeWorker(idx, newSelector, newWaker)
              newWorker.setName(idx)
              newWorker.setDaemon(true)
              workers(idx) = newWorker
              newWorker.start()
            } else {
              state.getAndIncrement()
              worker.setName(idx)
              workers(idx) = worker
              worker.blocking = false
              worker.active = true
              workerWaker.wake()
            }
          }
        }
      }
    }

  /**
   * Wakes up a worker using NIO waker instead of LockSupport.unpark()
   */
  private def maybeWakeWorker(currentState: Int): Unit = {
    val currentSearching = currentState & 0xffff
    val currentActive    = (currentState & 0xffff0000) >> 16
    if (currentActive != poolSize && currentSearching == 0) {
      val worker = idle.poll()
      if (worker ne null) {
        state.getAndAdd(0x10001)
        worker.active = true
        worker.workerWaker.wake()
      }
    }
  }

  private[this] def submitBlocking(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
    Blocking.blockingExecutor.submit(runnable)

  /**
   * Shutdown the scheduler gracefully
   */
  def shutdown(): Unit = {
    running = false
    workers.foreach { worker =>
      worker.interrupt()
      worker.workerWaker.wake()
    }
  }
}

private object NioScheduler {
  private val poolSize = java.lang.Runtime.getRuntime.availableProcessors

  def markCurrentWorkerAsBlocking(): Unit = {
    val worker = workerOrNull()
    if (worker ne null) {
      worker.markAsBlocking()
    } else {
      ()
    }
  }

  /**
   * If the current thread is a [[NioScheduler.Worker]] then it is returned,
   * otherwise returns null
   */
  private def workerOrNull(): NioScheduler.Worker =
    Thread.currentThread() match {
      case w: NioScheduler.Worker => w
      case _                    => null
    }

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

  private object Locations {

    final class Enabled(sizeHint: Int = 64) extends Locations {
      import scala.collection.mutable
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

  /**
   * A `Waker` uses a Pipe to wake up a Selector from select() without
   * using LockSupport.unpark(). This integrates better with NIO event loops.
   */
  private final class Waker(selector: Selector) {
    private val pipe = Pipe.open()
    private val source = pipe.source()
    private val sink = pipe.sink()
    private val buffer = ByteBuffer.allocate(1)
    private val woken = new AtomicBoolean(false)

    // Configure non-blocking mode
    source.configureBlocking(false)
    sink.configureBlocking(false)

    // Register source channel with selector
    source.register(selector, SelectionKey.OP_READ)

    /**
     * Wakes up the selector by writing to the pipe.
     * Uses atomic flag to prevent redundant wakeups.
     */
    def wake(): Unit = {
      if (woken.compareAndSet(false, true)) {
        try {
          buffer.clear()
          buffer.put(0.toByte)
          buffer.flip()
          sink.write(buffer)
          selector.wakeup()
        } catch {
          case _: IOException => ()
        }
      }
    }

    /**
     * Clears the wake signal by reading from the pipe.
     * Should be called after the selector detects the event.
     */
    def clear(): Unit = {
      try {
        buffer.clear()
        var read = 0
        while (read >= 0) {
          read = source.read(buffer)
          buffer.clear()
        }
        woken.set(false)
      } catch {
        case _: IOException => ()
      }
    }

    def close(): Unit = {
      try {
        source.close()
        sink.close()
      } catch {
        case _: IOException => ()
      }
    }
  }

  /**
   * A `Supervisor` is a `Thread` that monitors workers and shifts tasks
   * from blocking workers to new workers.
   */
  private sealed abstract class Supervisor extends Thread

  /**
   * A `Worker` is a `Thread` that executes actions using NIO Selector
   * for efficient event-driven scheduling.
   */
  private sealed abstract class Worker extends Thread with BlockContext {

    val submittedLocations: Locations

    /**
     * The Selector used for efficient waiting
     */
    def workerSelector: Selector

    /**
     * The Waker used to wake up this worker
     */
    def workerWaker: Waker

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
    val localQueue: RingBufferPow2[Runnable] =
      RingBufferPow2[Runnable](256)

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

    def markAsBlocking(): Unit

    final def setName(i: Int): Unit =
      setName(s"NioScheduler-Worker-$i")

    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      markAsBlocking()
      thunk
    }
  }
}
