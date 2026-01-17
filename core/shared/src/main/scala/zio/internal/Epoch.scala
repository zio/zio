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

import java.util.concurrent.atomic.{AtomicInteger, AtomicReferenceArray}

/**
 * Epoch represents a generation of fiber storage in the FiberSet.
 *
 * ==Lifecycle==
 *   1. Created as ACTIVE - accepts strong refs via add() 2. Transitions to
 *      ROTATING when full - converts strong→weak 3. Becomes ARCHIVED - only
 *      contains weak refs (CleanupRef) 4. Eventually retired via carry-forward
 *      when archive cap exceeded
 *
 * ==Slot Contents by State==
 *   - ACTIVE: FiberRef | null
 *   - ROTATING: FiberRef | CleanupRef | null (transitional)
 *   - ARCHIVED: CleanupRef | null
 *
 * ==Thread Safety==
 * All fields are atomic. Slot access uses CAS for safe concurrent modification.
 * State transitions are single-winner via CAS.
 *
 * @param id
 *   unique epoch identifier (global counter, never wraps in practice)
 * @param capacity
 *   maximum slots in this epoch
 */
private[internal] final class Epoch(val id: Long, capacity: Int) {
  import FiberSet.{ACTIVE, ROTATING, ARCHIVED}

  /**
   * Tagged slot array. Contents depend on epoch state:
   *   - ACTIVE: slots contain FiberRef (strong) or null
   *   - ROTATING: transitional, may contain either representation
   *   - ARCHIVED: slots contain CleanupRef (weak) or null
   */
  val slots: AtomicReferenceArray[AnyRef] = new AtomicReferenceArray(capacity)

  /**
   * Next available slot index. Incremented atomically on add(). May exceed
   * capacity briefly during rotation trigger.
   */
  val nextIndex: AtomicInteger = new AtomicInteger(0)

  /**
   * Current epoch state. Transitions: ACTIVE → ROTATING → ARCHIVED State
   * changes are single-winner via CAS.
   */
  val state: AtomicInteger = new AtomicInteger(ACTIVE)

  /**
   * Check if this epoch is in active state (accepting new fibers).
   */
  def isActive: Boolean = state.get() == ACTIVE

  /**
   * Check if this epoch is currently rotating (transitioning to archived).
   */
  def isRotating: Boolean = state.get() == ROTATING

  /**
   * Check if this epoch is archived (contains only weak refs).
   */
  def isArchived: Boolean = state.get() == ARCHIVED

  /**
   * Current fill level (may be > capacity during rotation trigger).
   */
  def size: Int = math.min(nextIndex.get(), capacity)

  /**
   * Debug string representation.
   */
  override def toString: String = {
    val stateStr = state.get() match {
      case ACTIVE   => "ACTIVE"
      case ROTATING => "ROTATING"
      case ARCHIVED => "ARCHIVED"
      case other    => s"UNKNOWN($other)"
    }
    s"Epoch(id=$id, state=$stateStr, size=${size}/$capacity)"
  }
}
