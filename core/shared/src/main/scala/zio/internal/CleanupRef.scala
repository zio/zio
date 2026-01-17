/*
 * Copyright 2024-2026 ZIO Contributors
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

import java.lang.ref.{ReferenceQueue, WeakReference}

/**
 * Self-locating weak reference for FiberSet cleanup.
 *
 * When the GC collects the referenced fiber, this ref is enqueued to the
 * ReferenceQueue. The epochId and slotIndex allow O(1) cleanup: we know exactly
 * which slot to clear without scanning.
 *
 * ==Lifecycle==
 *   1. Created during epoch rotation (strong→weak conversion) 2. Stored in
 *      archived epoch's slot 3. When fiber is GC'd, ref is enqueued to cleanup
 *      queue 4. drainQueue() uses epochId+slotIndex for O(1) slot clearing
 *
 * ==Memory Efficiency==
 * Unlike wrapping every fiber in WeakReference on add(), CleanupRefs are only
 * created for fibers that survive epoch rotation ("vampires"). Short-lived
 * "mayfly" fibers never allocate a CleanupRef.
 *
 * @param fiber
 *   the fiber being tracked (weak reference)
 * @param queue
 *   the cleanup queue to enqueue to when fiber is collected
 * @param epochId
 *   which epoch this ref belongs to
 * @param slotIndex
 *   which slot in the epoch's array
 */
private[internal] final class CleanupRef(
  fiber: FiberSetRef,
  queue: ReferenceQueue[FiberSetRef],
  val epochId: Long,
  val slotIndex: Int
) extends WeakReference[FiberSetRef](fiber, queue) {

  /**
   * Debug string representation.
   */
  override def toString: String = {
    val fiberStr = Option(get()).map(_.toString).getOrElse("<collected>")
    s"CleanupRef(epoch=$epochId, slot=$slotIndex, fiber=$fiberStr)"
  }
}
