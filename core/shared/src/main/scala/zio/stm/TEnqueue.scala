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
 * The write side of a transactional queue: a [[TQueue]] or a [[THub]]. Its
 * operations, apart from `capacity`, are STM transactions, so offers compose
 * with other transactional operations and commit together.
 *
 * What an offer does when the queue is full depends on how the queue was
 * created: a bounded queue retries the transaction, so the fiber suspends until
 * there is room; a dropping queue discards the new value; a sliding queue
 * discards the oldest value. After the queue is shut down, `capacity`,
 * `isShutdown`, `awaitShutdown` and `shutdown` still work, and the other
 * operations interrupt the fiber that runs them, except `offerAll` of an empty
 * collection on a [[THub]], which succeeds with `true`.
 */
sealed trait TEnqueue[-A] extends Serializable {

  /**
   * The capacity the queue was created with, or `Int.MaxValue` for an unbounded
   * queue.
   */
  def capacity: Int

  /**
   * Checks whether the queue is shut down.
   */
  def isShutdown: USTM[Boolean]

  /**
   * Offers a value to the queue. Succeeds with `false` when a dropping queue is
   * full and discards the value, and with `true` otherwise. A full bounded
   * queue retries, so the fiber suspends until there is room; a full sliding
   * queue drops its oldest value to make room. A [[THub]] with no subscribers
   * discards the value and succeeds with `true`.
   */
  def offer(a: A): ZSTM[Any, Nothing, Boolean]

  /**
   * Offers the values to the queue in order. When there is not room for all
   * of them, a bounded queue retries, so the fiber suspends until the queue
   * changes. A dropping queue adds the values that fit and succeeds with
   * `false` when it discards any. A sliding queue succeeds with `true`. A
   * [[THub]] with no subscribers discards the values and succeeds with `true`.
   */
  def offerAll(as: Iterable[A]): ZSTM[Any, Nothing, Boolean]

  /**
   * Shuts down the queue. Shutting down a [[THub]] also shuts down its
   * subscriptions.
   */
  def shutdown: USTM[Unit]

  /**
   * The current number of values in the queue.
   */
  def size: USTM[Int]

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
}
private[zio] object TEnqueue {
  private[zio] trait Internal[-A] extends TEnqueue[A]
}
