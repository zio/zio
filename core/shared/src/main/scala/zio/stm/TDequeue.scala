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
import zio.stm.ZSTM.internal.Journal

/**
 * A transactional queue that can only be dequeued.
 */
sealed trait TDequeue[+A, +E] extends Serializable {

  /**
   * The maximum capacity of the queue.
   */
  def capacity: Int

  /**
   * Checks whether the queue is shut down.
   */
  def isShutdown: ZSTM[Any, E, Boolean]

  /**
   * Views the next element in the queue without removing it, retrying if the
   * queue is empty.
   */
  def peek: ZSTM[Any, E, A]

  /**
   * Views the next element in the queue without removing it, returning `None`
   * if the queue is empty.
   */
  def peekOption: ZSTM[Any, E, Option[A]]

  /**
   * Shuts down the queue.
   */
  def shutdown: ZSTM[Any, E, Unit]

  /**
   * Shuts down the queue with a specific error.
   */
  def shutdown[E1 >: E](e: E1): ZSTM[Any, E1, Unit]

  /**
   * The current number of values in the queue.
   */
  def size: ZSTM[Any, E, Int]

  /**
   * Takes a value from the queue.
   */
  def take: ZSTM[Any, E, A]

  /**
   * Takes all the values from the queue.
   */
  def takeAll: ZSTM[Any, E, Chunk[A]]

  /**
   * Takes up to the specified number of values from the queue.
   */
  def takeUpTo(max: Int): ZSTM[Any, E, Chunk[A]]

  /**
   * Waits for the hub to be shut down.
   */
  def awaitShutdown: ZSTM[Any, E, Unit] =
    isShutdown.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)

  /**
   * Checks if the queue is empty.
   */
  def isEmpty: ZSTM[Any, E, Boolean] =
    size.map(_ == 0)

  /**
   * Checks if the queue is at capacity.
   */
  def isFull: ZSTM[Any, E, Boolean] =
    size.map(_ == capacity)

  /**
   * Takes a single element from the queue, returning `None` if the queue is
   * empty.
   */
  final def poll: ZSTM[Any, E, Option[A]] =
    takeUpTo(1).map(_.headOption)

  /**
   * Drops elements from the queue while they do not satisfy the predicate,
   * taking and returning the first element that does satisfy the predicate.
   * Retries if no elements satisfy the predicate.
   */
  final def seek(f: A => Boolean): ZSTM[Any, E, A] =
    take.flatMap(b => if (f(b)) ZSTM.succeedNow(b) else seek(f))

  /**
   * Takes a number of elements from the queue between the specified minimum and
   * maximum. If there are fewer than the minimum number of elements available,
   * retries until at least the minimum number of elements have been collected.
   */
  final def takeBetween(min: Int, max: Int): ZSTM[Any, E, Chunk[A]] =
    ZSTM.suspend {

      def takeRemainder(min: Int, max: Int, acc: Chunk[A]): ZSTM[Any, E, Chunk[A]] =
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
   * than the specified number of elements available, it retries until they
   * become available.
   */
  final def takeN(n: Int): ZSTM[Any, E, Chunk[A]] =
    takeBetween(n, n)
}
private[zio] object TDequeue {
  private[zio] trait Internal[+A, +E] extends TDequeue[A, E] {
    private[zio] def checkShutdown(journal: Journal, fiberId: FiberId): Unit
  }
}
