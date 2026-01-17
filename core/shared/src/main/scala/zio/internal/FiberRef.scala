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

/**
 * Interface for objects trackable by FiberSet.
 *
 * In ZIO integration, this would be mixed into FiberRuntime. The two locator
 * fields enable O(1) removal.
 *
 * ==Required Fields==
 * Implementing classes must provide mutable storage for:
 *   - `_setEpochId`: Long - which epoch this fiber is stored in (-1 if not in
 *     set)
 *   - `_setIndex`: Int - which slot index within the epoch (-1 if not in set)
 *
 * ==Thread Safety==
 * These fields are written by FiberSet operations (add/remove/carry-forward)
 * and read by remove(). They should be @volatile.
 *
 * ==Integration Notes==
 * For ZIO's FiberRuntime, add these fields:
 * {{{
 * @volatile private[zio] var _setEpochId: Long = -1L
 * @volatile private[zio] var _setIndex: Int = -1
 * }}}
 *
 * And implement isTerminated to return the fiber's termination status.
 */
trait FiberSetRef {

  /**
   * Epoch ID where this fiber is stored. -1 means not currently in any
   * FiberSet.
   */
  var _setEpochId: Long

  /**
   * Slot index within the epoch. -1 means not currently in any FiberSet.
   */
  var _setIndex: Int

  /**
   * Check if this fiber has terminated. Used during carry-forward to avoid
   * rehoming dead fibers.
   */
  def isTerminated: Boolean
}

/**
 * Test implementation of FiberRef for unit testing. Not for production use.
 */
final class TestFiber(val id: Int) extends FiberSetRef {
  @volatile var _setEpochId: Long            = -1L
  @volatile var _setIndex: Int               = -1
  @volatile private var _terminated: Boolean = false

  def isTerminated: Boolean = _terminated

  def terminate(): Unit =
    _terminated = true

  override def toString: String = s"TestFiber($id, terminated=${_terminated})"

  override def hashCode(): Int = id

  override def equals(obj: Any): Boolean = obj match {
    case other: TestFiber => this.id == other.id
    case _                => false
  }
}
