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

import java.util.concurrent.atomic.{AtomicInteger, AtomicLongArray}
import java.util.concurrent.{ConcurrentLinkedQueue, ThreadLocalRandom}
import scala.concurrent.{BlockContext, CanAwait}

/**
 * A `ZScheduler` is an `Executor` that is optimized for running ZIO applications.
 * This implementation adds a Hybrid Power-of-Two-Choices (P2C) proactive dispatcher
 * for O(1) load balancing with zero false sharing.
 */
private final class ZScheduler(autoBlocking: Boolean) extends Executor { parent =>

  import Trace.{empty => emptyTrace}
  import ZScheduler.{poolSize, workerOrNull}

  private[this] val globalQueue     = new PartitionedLinkedQueue[Runnable](poolSize * 4)
  private[this] val cache           = new ConcurrentLinkedQueue[ZScheduler.Worker]()
  private[this] val idle            = new ConcurrentLinkedQueue[ZScheduler.Worker]()
  private[this] val globalLocations = makeLocations()
  private[this] val state           = new AtomicInteger(poolSize << 16)
  private[this] val workers         = Array.ofDim[ZScheduler.Worker](poolSize)

  // 128-byte stride (16 Longs) → prevents false sharing on high-core NUMA systems
  private[this] val taskCounts      = new AtomicLongArray(poolSize * 16)

  @volatile private[this] var blockingLocations: Set[Trace] = Set.empty

  (0 until poolSize).foreach { workerId =>
    val worker = makeWorker(workerId)
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

  /** P2C: Power-of-Two-Choices with zero-load short-circuit (O(1) dispatch) */
  private[this] def chooseTargetWorker(): ZScheduler.Worker = {
    val rnd  = ThreadLocalRandom.current()
    val idxA = rnd.nextInt(poolSize)
    val idxB = rnd.nextInt(poolSize)

    val loadA = taskCounts.get(idxA << 4)
    val loadB = taskCounts.get(idxB << 4)

    if (loadA == 0L) workers(idxA)
    else if (loadB == 0L) workers(idxB)
    else if (loadA <= loadB) workers(idxA)
    else workers(idxB)
  }

  override private[zio] def isCurrentThreadInExecutor: Boolean =
    Thread.currentThread().isInstanceOf[ZScheduler.Worker]

  override def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = workerOrNull()

    if (isBlocking(worker, runnable)) {
      submitBlocking(runnable)
    } else {
      // 1. Local affinity (cache-hot path)
      if ((worker ne null) && !worker.blocking && worker.localQueue.offer(runnable)) {
        taskCounts.addAndGet(worker.id << 4, 1L)
      } else {
        // 2. P2C proactive dispatch
        val target = chooseTargetWorker()
        if (target.localQueue.offer(runnable)) {
          taskCounts.addAndGet(target.id << 4, 1L)
        } else {
          // 3. Global fallback
          globalQueue.offer(runnable)
        }
      }
      val currentState = state.get
      maybeUnparkWorker(currentState)
      true
    }
  }

  private[this] def makeWorker(workerId: Int): ZScheduler.Worker =
    new ZScheduler.Worker {
      override val id: Int = workerId
      override val submittedLocations: ZScheduler.Locations = makeLocations()

      final override def run(): Unit = {
        // === EVERYTHING BELOW THIS LINE IS THE ORIGINAL ZIO WORKER LOOP ===
        // (Copy-paste your existing run() body from the file here — it stays 100% unchanged)
        // Only add the counter decrement where the task finishes, e.g. right after runnable.run():

        // AFTER runnable.run() in the loop:
        // taskCounts.addAndGet(id << 4, -1L)

        // (If you have steal logic in the file, add similar +N / -N updates using stealer.id and victim.id)
      }
    }
}
