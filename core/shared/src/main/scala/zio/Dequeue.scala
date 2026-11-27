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
 * A queue that can only be dequeued: the taking side of a [[Queue]], and the
 * subscription that `Hub#subscribe` returns.
 */
sealed trait Dequeue[+A] extends Serializable {

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
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   * The wait can be interrupted. Interrupted when the queue is shut down.
   */
  def take(implicit trace: Trace): UIO[A]

  /**
   * Removes all the values in the queue and returns the values. If the queue is
   * empty returns an empty collection. It does not wait.
   */
  def takeAll(implicit trace: Trace): UIO[Chunk[A]]

  /**
   * Takes up to `max` values from the queue, oldest first. It does not wait: it
   * returns an empty `Chunk` when the queue is empty or `max` is 0. With a
   * negative `max`, it returns an empty `Chunk` on a `Queue` and on a
   * subscription to a bounded, dropping or sliding hub, and every waiting
   * message on a subscription to an unbounded hub.
   */
  def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]]

  /**
   * Checks whether the queue is currently empty.
   */
  def isEmpty(implicit trace: Trace): UIO[Boolean] =
    size.map(_ == 0)

  /**
   * Checks whether the queue is currently full.
   */
  def isFull(implicit trace: Trace): UIO[Boolean] =
    size.map(_ == capacity)

  /**
   * Takes a number of elements from the queue between the specified minimum and
   * maximum. If there are fewer than the minimum number of elements available,
   * suspends until at least the minimum number of elements have been collected.
   * Returns an empty `Chunk` right away when `max` is less than `min`.
   * Otherwise, when `min` is 0 or less, it does not wait and returns what
   * `takeUpTo(max)` returns.
   */
  final def takeBetween(min: Int, max: Int)(implicit trace: Trace): UIO[Chunk[A]] = {
    def takeRemainder(min: Int, max: Int, acc: Chunk[A]): UIO[Chunk[A]] =
      if (max < min) ZIO.succeed(acc)
      else
        takeUpTo(max).flatMap { bs =>
          val remaining = min - bs.length

          if (remaining <= 0) Exit.succeed(acc ++ bs)
          else if (remaining == 1) take.map(b => acc ++ bs :+ b)
          else take.flatMap(b => takeRemainder(remaining - 1, max - bs.length - 1, acc ++ bs :+ b))
        }

    takeRemainder(min, max, Chunk.empty)
  }

  /**
   * Takes the specified number of elements from the queue. If there are fewer
   * than the specified number of elements available, it suspends until they
   * become available. When `n` is 0 or less, it does not wait and returns what
   * `takeUpTo(n)` returns.
   */
  final def takeN(n: Int)(implicit trace: Trace): UIO[Chunk[A]] =
    takeBetween(n, n)

  /**
   * Takes the oldest value in the queue, in a `Some`, or returns `None` when
   * the queue is empty. It does not wait.
   */
  def poll(implicit trace: Trace): UIO[Option[A]] =
    takeUpTo(1).map(_.headOption)
}
private[zio] object Dequeue {
  private[zio] abstract class Internal[+A] extends Dequeue[A]
}
