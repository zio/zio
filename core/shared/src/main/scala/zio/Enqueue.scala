/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

package zio

/**
 * A queue that can only be enqueued: the offering side of a [[Queue]], and the
 * publishing side of a [[Hub]].
 */
sealed trait Enqueue[-A] extends Serializable {

  /**
   * Waits until the queue is shutdown. The `IO` returned by this method will
   * not resume until the queue has been shutdown. If the queue is already
   * shutdown, the `IO` will resume right away.
   */
  def awaitShutdown(implicit trace: Trace): UIO[Unit]

  /**
   * The number of elements the queue can hold: the requested capacity for a
   * bounded, dropping or sliding queue or hub, and `Int.MaxValue` for an
   * unbounded one.
   */
  def capacity: Int

  /**
   * `true` if `shutdown` has been called.
   */
  def isShutdown(implicit trace: Trace): UIO[Boolean]

  /**
   * Places one value in the queue. When the queue is full, a back-pressured
   * queue or hub (`bounded`) suspends the fiber until there is room, a dropping
   * one drops the value and returns `false`, and a sliding one drops the oldest
   * value to make room and returns `true`. Interrupted when the queue is shut
   * down.
   */
  def offer(a: A)(implicit trace: Trace): UIO[Boolean]

  /**
   * Places the values in the queue, in order, and returns the ones that it did
   * not place.
   *
   * For Bounded Queue: uses the `BackPressure` Strategy, places the values in
   * the queue and returns no leftovers. If the queue has reached capacity, then
   * the fiber performing the `offerAll` will be suspended until there is room
   * in the queue. When that fiber is interrupted, the values already placed
   * stay in the queue.
   *
   * For Unbounded Queue: Places all values in the queue and returns no
   * leftovers.
   *
   * For Sliding Queue: uses `Sliding` Strategy. If there is room in the queue,
   * it places the values, otherwise it removes the oldest elements and enqueues
   * the new ones. Returns no leftovers.
   *
   * For Dropping Queue: uses `Dropping` Strategy. It places the values in the
   * queue but if there is no room it will not enqueue them and return the
   * leftovers.
   *
   * A `Hub` made with the same strategy behaves the same way. Interrupted when
   * the queue is shut down.
   */
  def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]]

  /**
   * Shuts down the queue. On a `Queue`, it interrupts the fibers suspended in
   * `offer` or `take`. On a `Hub`, it shuts down every subscription, which
   * interrupts the fibers suspended in `take`, and frees the space that the
   * subscriptions held, so publishers suspended by back pressure resume with
   * `true`. On a hub subscription, it interrupts the fibers suspended in `take`
   * on that subscription.
   *
   * After it, calls to `offer*`, `take*`, `poll` and `size` are interrupted
   * immediately, while `isShutdown`, `awaitShutdown` and `shutdown` succeed.
   */
  def shutdown(implicit trace: Trace): UIO[Unit]

  /**
   * Retrieves the size of the queue. For a `Queue`, this may be negative if
   * fibers are suspended waiting for elements to be added to the queue or
   * greater than the capacity if fibers are suspended waiting to add elements
   * to the queue. For a `Hub`, it is the number of messages that the hub holds,
   * and for a hub subscription the number of messages waiting to be taken.
   * Interrupted when the queue is shut down.
   */
  def size(implicit trace: Trace): UIO[Int]

  /**
   * Checks whether the queue is currently empty.
   */
  def isEmpty(implicit trace: Trace): UIO[Boolean] =
    size.map(_ <= 0)

  /**
   * Checks whether the queue is currently full.
   */
  def isFull(implicit trace: Trace): UIO[Boolean] =
    size.map(_ >= capacity)
}
private[zio] object Enqueue {
  private[zio] trait Internal[-A] extends Enqueue[A]
}
