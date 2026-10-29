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

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference, LongAdder}
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ThreadLocalRandom}
import scala.concurrent.{BlockContext, CanAwait}

/**
 * A `NIOScheduler` is an `Executor` inspired by the NIO scheduler design,
 * combining rotating-batch least-loaded worker dispatch with per-worker ring
 * buffers, a LIFO hot slot, and work-stealing overflow queues.
 */
private[zio] final class NIOScheduler(
  poolSize0: Int,
  batchSize0: Int,
  autoBlocking: Boolean
) extends Executor { parent =>

  def this(autoBlocking: Boolean) =
    this(
      java.lang.Runtime.getRuntime.availableProcessors,
      NIOScheduler.DefaultBatchSize,
      autoBlocking
    )

  def this() =
    this(false)

  import NIOScheduler.{LocalBalanceThreshold, LocalQueueCapacity, SpillBatchSize}
  import Trace.{empty => emptyTrace}

  val poolSize: Int  = Math.max(1, poolSize0)
  val batchSize: Int = Math.max(1, Math.min(batchSize0, poolSize))

  private[this] val globalQueue      = new PartitionedLinkedQueue[Runnable](poolSize * 4)
  private[this] val cache            = new ConcurrentLinkedQueue[NIOScheduler.Worker]()
  private[this] val idle             = new ConcurrentLinkedQueue[NIOScheduler.Worker]()
  private[this] val globalLocations  = makeLocations()
  private[this] val activeWorkers    = new AtomicInteger(poolSize)
  private[this] val searchingWorkers = new AtomicInteger(0)
  private[this] val batchCursor      = new AtomicInteger(0)
  private[this] val totalEnqueued    = new LongAdder()
  private[this] val totalDequeued    = new LongAdder()
  private[this] val isShutdownFlag   = new AtomicBoolean(false)
  private[this] val workers          = Array.ofDim[NIOScheduler.Worker](poolSize)

  @volatile private[this] var blockingLocations: Set[Trace] = Set.empty

  private[this] val supervisor: NIOScheduler.Supervisor =
    if (autoBlocking) {
      val sup = makeSupervisor()
      sup.setName("NIOScheduler-Supervisor")
      sup.setDaemon(true)
      sup
    } else null

  (0 until poolSize).foreach { workerId =>
    val worker = makeWorker()
    worker.setName(workerId)
    worker.setDaemon(true)
    workers(workerId) = worker
  }
  workers.foreach(_.start())
  if (supervisor ne null) supervisor.start()

  override private[zio] def isCurrentThreadInExecutor: Boolean =
    workerOrNull() ne null

  def shutdown(): Unit =
    if (isShutdownFlag.compareAndSet(false, true)) {
      if (supervisor ne null) {
        LockSupport.unpark(supervisor)
      }
      var i = 0
      while (i < poolSize) {
        val w = workers(i)
        if (w ne null) {
          LockSupport.unpark(w)
        }
        i += 1
      }
      var cached = cache.poll()
      while (cached ne null) {
        LockSupport.unpark(cached)
        cached = cache.poll()
      }
      var parked = idle.poll()
      while (parked ne null) {
        LockSupport.unpark(parked)
        parked = idle.poll()
      }
    }

  def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] = {
    val metrics = new ExecutionMetrics {
      def capacity: Int =
        Int.MaxValue
      def concurrency: Int =
        poolSize
      def dequeuedCount: Long =
        totalDequeued.sum()
      def enqueuedCount: Long =
        Math.max(totalDequeued.sum(), totalEnqueued.sum())
      def size: Int = {
        var i    = 0
        var size = 0
        while (i != poolSize) {
          val worker = workers(i)
          size += worker.localQueue.size()
          if (worker.nextRunnable.get() ne null) size += 1
          i += 1
        }
        size += globalQueue.size()
        size
      }
      def workersCount: Int =
        Math.max(0, activeWorkers.get())
    }
    Some(metrics)
  }

  override def stealWork(depth: Int): Boolean = {
    val worker = workerOrNull()
    if ((worker ne null) && !worker.blocking) {
      var runnable = worker.nextRunnable.getAndSet(null)
      if (runnable eq null) {
        runnable = worker.localQueue.poll(null)
        if (runnable eq null) {
          runnable = globalQueue.poll()
        }
      }

      if (runnable ne null) {
        val prevRunnable = worker.currentRunnable
        worker.currentRunnable = runnable
        try {
          if (runnable.isInstanceOf[FiberRunnable]) {
            runnable.asInstanceOf[FiberRunnable].run(depth)
          } else {
            runnable.run()
          }
        } finally {
          worker.opCount += 1L
          totalDequeued.increment()
          worker.currentRunnable = prevRunnable
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
    val worker = workerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      totalEnqueued.increment()
      if ((worker ne null) && !worker.blocking && worker.localQueue.size() < LocalBalanceThreshold) {
        if (!worker.localQueue.offer(runnable)) {
          handleFullWorkerQueue(worker, runnable)
        }
        if (worker.blocking) {
          drainBlockingWorker(worker)
        }
        maybeUnparkWorker()
      } else {
        val target = selectLeastLoadedWorker()
        if ((target ne null) && !target.blocking && target.localQueue.offer(runnable)) {
          if (target.blocking) {
            drainBlockingWorker(target)
            maybeUnparkWorker()
          } else {
            unparkSpecificOrIdleWorker(target)
          }
        } else if (target ne null) {
          handleFullWorkerQueue(target, runnable)
          if (target.blocking) {
            drainBlockingWorker(target)
          }
          maybeUnparkWorker()
        } else {
          globalQueue.offer(runnable)
          maybeUnparkWorker()
        }
      }
      true
    }
  }

  override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = workerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      totalEnqueued.increment()
      var notify = true
      if ((worker eq null) || worker.blocking) {
        val target = selectLeastLoadedWorker()
        if ((target ne null) && !target.blocking && target.localQueue.offer(runnable)) {
          if (target.blocking) {
            drainBlockingWorker(target)
          } else {
            unparkSpecificOrIdleWorker(target)
            notify = false
          }
        } else if (target ne null) {
          handleFullWorkerQueue(target, runnable)
          if (target.blocking) {
            drainBlockingWorker(target)
          }
        } else {
          globalQueue.offer(runnable)
        }
      } else if ((worker.nextRunnable.get() eq null) && worker.localQueue.isEmpty()) {
        val fromGlobal = globalQueue.poll()
        if (fromGlobal eq null) {
          if (worker.nextRunnable.compareAndSet(null, runnable)) {
            if (worker.blocking) {
              drainBlockingWorker(worker)
            } else {
              notify = false
            }
          } else if (!worker.localQueue.offer(runnable)) {
            handleFullWorkerQueue(worker, runnable)
          }
        } else {
          if (!worker.nextRunnable.compareAndSet(null, fromGlobal)) {
            if (!worker.localQueue.offer(fromGlobal)) {
              handleFullWorkerQueue(worker, fromGlobal)
            }
          }
          if (!worker.localQueue.offer(runnable)) {
            handleFullWorkerQueue(worker, runnable)
          }
          if (worker.blocking) {
            drainBlockingWorker(worker)
          }
        }
      } else {
        if (!worker.localQueue.offer(runnable)) {
          handleFullWorkerQueue(worker, runnable)
        }
        if (worker.blocking) {
          drainBlockingWorker(worker)
        }
      }

      if (notify) {
        maybeUnparkWorker()
      }
      true
    }
  }

  private[this] def selectLeastLoadedWorker(): NIOScheduler.Worker = {
    val start                           = batchCursor.getAndAdd(batchSize)
    var i                               = 0
    var bestWorker: NIOScheduler.Worker = null
    var minLoad                         = Int.MaxValue

    while (i < batchSize) {
      val idx    = Math.floorMod(start + i, poolSize)
      val worker = workers(idx)
      if ((worker ne null) && !worker.blocking) {
        val load     = worker.localQueue.size() + (if (worker.nextRunnable.get() ne null) 1 else 0)
        val isActive = worker.active
        if (isActive && load == 0) {
          return worker
        }
        if (load < minLoad || (load == minLoad && isActive && ((bestWorker eq null) || !bestWorker.active))) {
          minLoad = load
          bestWorker = worker
        }
      }
      i += 1
    }

    bestWorker
  }

  private def drainBlockingWorker(worker: NIOScheduler.Worker): Unit =
    worker.synchronized {
      val hot = worker.nextRunnable.getAndSet(null)
      if (hot ne null) {
        globalQueue.offer(hot)
      }
      val runnables = worker.localQueue.pollUpTo(LocalQueueCapacity)
      if (!runnables.isEmpty) {
        globalQueue.offerAll(runnables)
      }
    }

  private def handleFullWorkerQueue(worker: NIOScheduler.Worker, runnable: Runnable): Unit = {
    val rnd    = ThreadLocalRandom.current
    val polled = worker.localQueue.pollUpTo(SpillBatchSize)
    if (!polled.isEmpty) {
      globalQueue.offerAll(polled, rnd)
    }
    if (!worker.localQueue.offer(runnable)) {
      globalQueue.offer(runnable, rnd)
    }
  }

  private[this] def workerOrNull(): NIOScheduler.Worker =
    Thread.currentThread() match {
      case w: NIOScheduler.Worker if w.scheduler eq parent => w
      case _                                               => null
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
          val workerCount = workers(i).submittedLocations.get(location)
          count += workerCount
          i += 1
        }
        count
      }

      override def run(): Unit = {
        val identifiedLocations = makeLocations()
        val previousOpCounts    = Array.fill(poolSize)(-1L)
        while (!isInterrupted && !isShutdownFlag.get()) {
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
              if (!currentWorker.localQueue.isEmpty() || (currentWorker.nextRunnable.get() ne null)) {
                unparkSpecificOrIdleWorker(currentWorker)
              }
              previousOpCounts(workerId) = -1L
            }
            workerId += 1
          }
          val deadline = java.lang.System.currentTimeMillis() + 100
          var loop     = true
          while (loop && !isInterrupted && !isShutdownFlag.get()) {
            LockSupport.parkUntil(deadline)
            loop = java.lang.System.currentTimeMillis() < deadline
          }
        }
      }
    }

  private[this] def makeWorker(): NIOScheduler.Worker =
    new NIOScheduler.Worker(parent) {
      self =>
      override val submittedLocations: NIOScheduler.Locations = makeLocations()

      final override def run(): Unit = {
        val globalQueue      = parent.globalQueue
        val workers          = parent.workers
        val activeWorkers    = parent.activeWorkers
        val searchingWorkers = parent.searchingWorkers
        val cache            = parent.cache
        val idle             = parent.idle
        val poolSize         = parent.poolSize

        var currentBlocking = false
        var currentOpCount  = 0L
        val random          = ThreadLocalRandom.current
        var runnable        = null.asInstanceOf[Runnable]
        var searching       = false

        while (!isInterrupted && !isShutdownFlag.get()) {
          currentBlocking = blocking
          if (currentBlocking) {
            runnable = null
          } else {
            val currentNextRunnable = nextRunnable.getAndSet(null)
            if (currentNextRunnable ne null) {
              runnable = currentNextRunnable
            } else {
              if ((currentOpCount & 63L) == 0L) {
                runnable = globalQueue.poll(random)
                if (runnable eq null) {
                  runnable = localQueue.poll(null)
                }
              } else {
                runnable = localQueue.poll(null)
                if (runnable eq null) {
                  runnable = globalQueue.poll(random)
                }
              }
              if (runnable eq null) {
                if (!searching) {
                  val currentSearching = searchingWorkers.get()
                  if (2 * currentSearching < poolSize) {
                    searchingWorkers.incrementAndGet()
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
                    if ((worker ne self) && (worker ne null) && !worker.blocking) {
                      val size = worker.localQueue.size()
                      if (size > 0) {
                        val runnables  = worker.localQueue.pollUpTo(size - size / 2)
                        val nRunnables = runnables.size
                        if (nRunnables > 0) {
                          val iter = runnables.iterator
                          runnable = iter.next()
                          if (nRunnables > 1) {
                            val leftover = localQueue.offerAll(iter, nRunnables - 1)
                            if (!leftover.isEmpty) {
                              globalQueue.offerAll(leftover, random)
                            }
                          }
                          currentBlocking = blocking
                          if (currentBlocking) {
                            val drained = localQueue.pollUpTo(LocalQueueCapacity)
                            if (!drained.isEmpty) {
                              globalQueue.offerAll(drained, random)
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
          }

          if (runnable eq null) {
            val wasSearching = searching
            val remainingSearching =
              if (searching) {
                searching = false
                searchingWorkers.decrementAndGet()
              } else {
                searchingWorkers.get()
              }
            var enteredIdle = false
            self.synchronized {
              active = false
              if (blocking) {
                drainBlockingWorker(self)
                cache.offer(self)
              } else {
                activeWorkers.decrementAndGet()
                idle.offer(self)
                parked.set(true)
                enteredIdle = true
              }
            }
            if (enteredIdle) {
              if ((nextRunnable.get() ne null) || !localQueue.isEmpty()) {
                tryActivateWorker(self)
                ()
              } else {
                var notify = !globalQueue.isEmpty()
                if (!notify && remainingSearching == 0 && wasSearching) {
                  var i = 0
                  while (i != poolSize && !notify) {
                    val worker = workers(i)
                    notify = (worker ne null) && (!worker.localQueue.isEmpty() || (worker.nextRunnable.get() ne null))
                    i += 1
                  }
                }
                if (notify) {
                  maybeUnparkWorker()
                }
              }
            }
            while (!active && !isInterrupted && !isShutdownFlag.get()) {
              LockSupport.park()
            }
            currentOpCount = opCount
            searching = !isInterrupted && !isShutdownFlag.get()
          } else {
            if (searching) {
              searching = false
              if (searchingWorkers.decrementAndGet() == 0) {
                maybeUnparkWorker()
              }
            }
            currentRunnable = runnable
            try {
              runnable.run()
            } finally {
              runnable = null
              currentOpCount = opCount + 1L
              opCount = currentOpCount
              totalDequeued.increment()
              currentRunnable = null
            }
          }
        }
      }

      final def markAsBlocking(): Unit = synchronized {
        if (blocking || !active || isShutdownFlag.get()) ()
        else {
          blocking = true
          val idx = workers.indexOf(self)
          if (idx >= 0) {
            drainBlockingWorker(self)
            val cachedWorker = cache.poll()
            if (cachedWorker eq null) {
              val fresh = makeWorker()
              fresh.setName(idx)
              fresh.setDaemon(true)
              workers(idx) = fresh
              fresh.start()
            } else {
              searchingWorkers.incrementAndGet()
              cachedWorker.setName(idx)
              workers(idx) = cachedWorker
              cachedWorker.blocking = false
              cachedWorker.parked.set(false)
              cachedWorker.active = true
              LockSupport.unpark(cachedWorker)
            }
          }
        }
      }
    }

  private def tryActivateWorker(worker: NIOScheduler.Worker): Boolean =
    if (worker ne null) {
      worker.synchronized {
        if (!worker.blocking && !worker.active && worker.parked.compareAndSet(true, false)) {
          idle.remove(worker)
          activeWorkers.incrementAndGet()
          searchingWorkers.incrementAndGet()
          worker.active = true
          LockSupport.unpark(worker)
          true
        } else false
      }
    } else false

  private def unparkSpecificOrIdleWorker(target: NIOScheduler.Worker): Unit =
    if (!tryActivateWorker(target)) {
      maybeUnparkWorker()
    }

  private def maybeUnparkWorker(): Unit =
    if (activeWorkers.get() < poolSize && searchingWorkers.get() == 0) {
      var loop = true
      while (loop) {
        val worker = idle.poll()
        if (worker eq null) {
          loop = false
        } else if (tryActivateWorker(worker)) {
          loop = false
        }
      }
    }

  private[this] def submitBlocking(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
    Blocking.blockingExecutor.submit(runnable)
}

private[zio] object NIOScheduler {
  private val DefaultBatchSize      = 4
  private val LocalQueueCapacity    = 256
  private val SpillBatchSize        = 128
  private val LocalBalanceThreshold = 64

  def apply(
    poolSize: Int = java.lang.Runtime.getRuntime.availableProcessors,
    batchSize: Int = DefaultBatchSize,
    autoBlocking: Boolean = false
  ): NIOScheduler =
    new NIOScheduler(poolSize, batchSize, autoBlocking)

  def markCurrentWorkerAsBlocking(): Unit =
    Thread.currentThread() match {
      case w: NIOScheduler.Worker => w.markAsBlocking()
      case _                      => ()
    }

  private sealed abstract class Locations {
    def get(trace: Trace): Long
    def put(trace: Trace): Long
  }

  private object Locations {
    final class Enabled extends Locations {
      private[this] val locations = new ConcurrentHashMap[Trace, AtomicLong](64)

      def get(trace: Trace): Long = {
        val v = locations.get(trace)
        if (v eq null) 0L else v.get()
      }

      def put(trace: Trace): Long = {
        var counter = locations.get(trace)
        if (counter eq null) {
          val fresh = new AtomicLong(0L)
          val prev  = locations.putIfAbsent(trace, fresh)
          counter = if (prev ne null) prev else fresh
        }
        counter.getAndIncrement()
      }
    }

    object Disabled extends Locations {
      def get(trace: Trace): Long = 0L
      def put(trace: Trace): Long = 0L
    }
  }

  private sealed abstract class Supervisor extends Thread

  private sealed abstract class Worker(val scheduler: NIOScheduler) extends Thread with BlockContext {

    val submittedLocations: Locations

    @volatile
    var active: Boolean =
      true

    @volatile
    var blocking: Boolean =
      false

    val parked: AtomicBoolean =
      new AtomicBoolean(false)

    @volatile
    var currentRunnable: Runnable =
      null

    val localQueue: RingBufferPow2[Runnable] =
      RingBufferPow2[Runnable](LocalQueueCapacity)

    val nextRunnable: AtomicReference[Runnable] =
      new AtomicReference[Runnable](null)

    @volatile
    var opCount: Long =
      0L

    def markAsBlocking(): Unit

    final def setName(i: Int): Unit =
      setName(s"NIOScheduler-Worker-$i")

    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      markAsBlocking()
      thunk
    }
  }
}
