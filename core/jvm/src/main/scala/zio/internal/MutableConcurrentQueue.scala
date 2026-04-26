/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.concurrent.Mailbox

/**
 * A lock-free concurrent queue implementation based on the algorithm described
 * in "Simple, Fast, and Practical Non-Blocking and Blocking Concurrent Queue
 * Algorithms" by Maged M. Michael and Michael L. Scott.
 *
 * This queue is designed for high-performance, concurrent access from multiple
 * producers and a single consumer.
 *
 * @note This queue is not bounded.
 */
trait MutableConcurrentQueue[+A] {

  /**
   * The maximum number of elements that a queue can hold.
   *
   * @note Int.MaxValue is treated as unbounded.
   */
  def capacity(): Int

  /**
   * Views the first element in the queue without removing it, if one exists.
   */
  def unsafePeek(): A

  /**
   * Removes all elements from the queue.
   */
  def clear(): Unit

  /**
   * Checks whether the queue is empty.
   */
  def isEmpty(): Boolean

  /**
   * Checks whether the queue is full.
   */
  def isFull(): Boolean

  /**
   * Removes the first element from the queue and returns it.
   *
   * @note This method may return null if the queue is empty.
   */
  def unsafePoll(): A

  /**
   * Inserts the element `a` into the queue.
   *
   * @return true if the operation succeeded, false otherwise
   */
  def offer[A1 >: A](a: A1): Boolean

  /**
   * Inserts the element `a` into the queue.
   *
   * @note This method does not check if the queue is full.
   */
  def unsafeOffer[A1 >: A](a: A1): Unit

  /**
   * Returns the number of elements in the queue.
   */
  def size(): Int
}