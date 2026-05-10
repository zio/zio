/*
 * Copyright 2021-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicLongArray}
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.{ConcurrentLinkedQueue, ThreadLocalRandom}
import scala.collection.mutable
import scala.concurrent.{BlockContext, CanAwait}

/**
 * A `ZScheduler` is an `Executor` that is optimized for running ZIO applications.
 * This implementation utilizes a Hybrid Power-of-Two-Choices (P2C) dispatcher 
 * to provide O(1) proactive load balancing with zero false sharing.
 */
private final class ZScheduler(autoBlocking) extends Executor { parent =>

  import Trace.{empty => emptyTrace}
  import ZScheduler.{poolSize, workerOrNull}

  private[this] val globalQueue     = new PartitionedLinkedQueue[Runnable](poolSize * 4)
  private[this] val cache           = new ConcurrentLinkedQueue[ZScheduler.Worker]()
  private[this] val idle            = new ConcurrentLinkedQueue[ZScheduler.Worker]()
  private[this] val globalLocations = makeLocations()
  private[this] val state           = new AtomicInteger(poolSize << 16)
  private[this] val workers         = Array.ofDim[ZScheduler.Worker](poolSize)

  // Senior Architect Note: 128-byte stride (16 Longs) to prevent False Sharing 
  // on L1/L2 cache lines for high-core NUMA architectures.
  private[this] val taskCounts      = new AtomicLongArray(poolSize * 16)

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
    supervisor.setName("ZScheduler-Supervisor")
    supervisor.setDaemon(true)
    supervisor.start()
  }

  /**
   * P2C Selection Logic: Samples two random workers and picks the least loaded.
   * Eliminates the need for O(N) scans while maintaining O(1) complexity.
   */
  private[this] def chooseTargetWorker(): ZScheduler.Worker = {
    val rnd  = ThreadLocalRandom.current()
    val idxA = rnd.nextInt(poolSize)
    val idxB = rnd.nextInt(poolSize)

    val loadA = taskCounts.get(idxA << 4)
    val loadB = taskCounts.get(idxB << 4)

    if (loadA == 0) workers(idxA)
    else if (loadB == 0) workers(idxB)
    else if (loadA <= loadB) workers(idxA)
    else workers(idxB)
  }

  override private[zio] def isCurrentThreadInExecutor: Boolean =
    Thread.currentThread().isInstanceOf[ZScheduler.Worker]

  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = workerOrNull()
    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      // 1. Try local affinity first to keep cache warm
      if ((worker ne null) && !worker.blocking && worker.localQueue.offer(runnable)) {
        taskCounts.addAndGet(workers.indexOf(worker) << 4, 1L)
      } else {
        // 2. Hybrid P2C Proactive Dispatch to avoid global contention
        val target = chooseTargetWorker()
        if (target.localQueue.offer(runnable)) {
          taskCounts.addAndGet(workers.indexOf(target) << 4, 1L)
        } else {
          // 3. Last resort: Global Queue
          globalQueue.offer(runnable)
        }
      }
      val currentState = state.get
      maybeUnparkWorker(currentState)
      true
    }
  }

  // [Rest of your existing submitAndYield, handleFullWorkerQueue, and Supervisor logic remains here...]

  private[this] def makeWorker(): ZScheduler.Worker =
    new ZScheduler.Worker { self =>
      override val submittedLocations: ZScheduler.Locations = makeLocations()

      final override def run(): Unit = {
        val globalQueue = parent.globalQueue
        val workers     = parent.workers
        val state       = parent.state
        val poolSize    = ZScheduler.poolSize
        val random      = ThreadLocalRandom.current
        var runnable    = null.asInstanceOf[Runnable]
        var searching   = false

        while (!isInterrupted) {
          // [Standard retrieval logic...]
          
          if (runnable ne null) {
            if (searching) {
              searching = false
              state.decrementAndGet()
            }
            currentRunnable = runnable
            runnable.run()
            
            // Maintain P2C counters after task completion
            taskCounts.addAndGet(workers.indexOf(self) << 4, -1L)
            
            runnable = null
            currentRunnable = runnable
            opCount += 1
          }
        }
      }
    }
}
