/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.stm

import zio._

/**
 * The read side of a transactional queue: a [[TQueue]], or a subscription
 * returned by [[THub#subscribe]]. Its operations, apart from `capacity`, are
 * STM transactions, so takes compose with other transactional operations and
 * commit together.
 *
 * Operations that wait for values (`take`, `peek`, `takeN`, `takeBetween`,
 * `seek`) retry the transaction: the fiber suspends until the queue changes,
 * then runs the transaction again. After the queue is shut down, `capacity`,
 * `isShutdown`, `awaitShutdown` and `shutdown` still work, and the other
 * operations interrupt the fiber that runs them, except `takeBetween` with
 * `max` less than `min`, which succeeds with an empty chunk.
 */
sealed trait TDequeue[+A] extends Serializable {

  /**
   * The capacity the queue was created with, or `Int.MaxValue` for an unbounded
   * queue. A hub subscription has the capacity of its hub.
   */
  def capacity: Int

  /**
   * Checks whether the queue is shut down. A hub subscription is shut down when
   * it or its hub is shut down.
   */
  def isShutdown: USTM[Boolean]

  /**
   * Views the next element in the queue without removing it, retrying if the
   * queue is empty, so the fiber suspends until a value arrives.
   */
  def peek: ZSTM[Any, Nothing, A]

  /**
   * Views the next element in the queue without removing it, returning `None`
   * if the queue is empty.
   */
  def peekOption: ZSTM[Any, Nothing, Option[A]]

  /**
   * Shuts down the queue. A fiber suspended in `take` on it wakes up and is
   * interrupted. Shutting down a hub subscription unsubscribes it from the hub.
   */
  def shutdown: USTM[Unit]

  /**
   * The current number of values in the queue.
   */
  def size: USTM[Int]

  /**
   * Takes the next value from the queue, retrying while the queue is empty, so
   * the fiber suspends until a value arrives.
   */
  def take: ZSTM[Any, Nothing, A]

  /**
   * Takes all the values from the queue without waiting. The result is empty
   * when the queue is empty.
   */
  def takeAll: ZSTM[Any, Nothing, Chunk[A]]

  /**
   * Takes up to `max` values from the queue without waiting. The result is
   * empty when the queue is empty or `max` is 0.
   */
  def takeUpTo(max: Int): ZSTM[Any, Nothing, Chunk[A]]

  /**
   * Retries until the queue is shut down, so the fiber suspends until another
   * fiber shuts it down.
   */
  def awaitShutdown: USTM[Unit] =
    isShutdown.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)

  /**
   * Checks if the queue is empty.
   */
  def isEmpty: USTM[Boolean] =
    size.map(_ == 0)

  /**
   * Checks if the queue is at capacity, that is, its size equals `capacity`.
   */
  def isFull: USTM[Boolean] =
    size.map(_ == capacity)

  /**
   * Takes a single element from the queue, returning `None` if the queue is
   * empty.
   */
  final def poll: ZSTM[Any, Nothing, Option[A]] =
    takeUpTo(1).map(_.headOption)

  /**
   * Drops elements from the queue while they do not satisfy the predicate,
   * taking and returning the first element that does satisfy the predicate.
   * When no element satisfies it, the transaction retries, which also undoes
   * the drops, and the fiber suspends until the queue changes.
   */
  final def seek(f: A => Boolean): ZSTM[Any, Nothing, A] =
    take.flatMap(b => if (f(b)) ZSTM.succeedNow(b) else seek(f))

  /**
   * Takes at least `min` and at most `max` elements from the queue. If fewer
   * than `min` elements are available, the transaction retries without taking
   * any, so the fiber suspends until at least `min` elements are in the queue.
   * When `max` is less than `min`, the result is empty and nothing is taken.
   */
  final def takeBetween(min: Int, max: Int): ZSTM[Any, Nothing, Chunk[A]] =
    ZSTM.suspend {

      def takeRemainder(min: Int, max: Int, acc: Chunk[A]): ZSTM[Any, Nothing, Chunk[A]] =
        if (max < min) ZSTM.succeedNow(acc)
        else
          takeUpTo(max).flatMap { bs =>
            val remaining = min - bs.length
            if (remaining == 1)
              take.map(b => acc ++ bs :+ b)
            else if (remaining > 1) {
              take.flatMap { b =>
                takeRemainder(remaining - 1, max - bs.length - 1, acc ++ bs :+ b)

              }
            } else
              ZSTM.succeedNow(acc ++ bs)
          }

      takeRemainder(min, max, Chunk.empty)
    }

  /**
   * Takes the specified number of elements from the queue. If there are fewer
   * than `n` elements available, the transaction retries without taking any, so
   * the fiber suspends until they become available. The result is empty when
   * `n` is 0.
   */
  final def takeN(n: Int): ZSTM[Any, Nothing, Chunk[A]] =
    takeBetween(n, n)
}
private[zio] object TDequeue {
  private[zio] trait Internal[+A] extends TDequeue[A]
}
