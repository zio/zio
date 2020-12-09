/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio.stm.ZSTM.internal._

import scala.collection.immutable.{ Queue => ScalaQueue }

final class TQueue[A] private (val capacity: Int, ref: TRef[ScalaQueue[A]]) {

  /**
   * Checks if the queue is empty.
   */
  def isEmpty: USTM[Boolean] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)
      TExit.Succeed(q.isEmpty)
    })

  /**
   * Checks if the queue is at capacity.
   */
  def isFull: USTM[Boolean] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)
      TExit.Succeed(q.size == capacity)
    })

  /**
   * Views the last element inserted into the queue, retrying if the queue is
   * empty.
   */
  def last: USTM[A] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)

      q.lastOption match {
        case Some(a) => TExit.Succeed(a)
        case None    => TExit.Retry
      }
    })

  /**
   * Offers the specified value to the queue, retrying if the queue is at
   * capacity.
   */
  def offer(a: A): USTM[Unit] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)

      if (q.length < capacity)
        TExit.Succeed(ref.unsafeSet(journal, q.enqueue(a)))
      else
        TExit.Retry
    })

  /**
   * Offers each of the elements in the specified collection to the queue up to
   * the maximum capacity of the queue, retrying if there is not capacity in
   * the queue for all of these elements. Returns any remaining elements in the
   * specified collection.
   */
  def offerAll(as: Iterable[A]): USTM[Iterable[A]] =
    new ZSTM((journal, _, _, _) => {
      val (forQueue, remaining) = as.splitAt(capacity)

      val q = ref.unsafeGet(journal)

      if (forQueue.size > capacity - q.length) TExit.Retry
      else {
        ref.unsafeSet(journal, q ++ forQueue)
        TExit.Succeed(remaining)
      }
    })

  /**
   * Views the next element in the queue without removing it, retrying if the
   * queue is empty.
   */
  def peek: USTM[A] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)

      q.headOption match {
        case Some(a) => TExit.Succeed(a)
        case None    => TExit.Retry
      }
    })

  /**
   * Views the next element in the queue without removing it, returning `None`
   * if the queue is empty.
   */
  def peekOption: USTM[Option[A]] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)
      TExit.Succeed(q.headOption)
    })

  /**
   * Takes a single element from the queue, returning `None` if the queue is
   * empty.
   */
  def poll: USTM[Option[A]] =
    takeUpTo(1).map(_.headOption)

  /**
   * Drops elements from the queue while they do not satisfy the predicate,
   * taking and returning the first element that does satisfy the predicate.
   * Retries if no elements satisfy the predicate.
   */
  def seek(f: A => Boolean): USTM[A] =
    new ZSTM((journal, _, _, _) => {
      var q    = ref.unsafeGet(journal)
      var loop = true
      var res  = null.asInstanceOf[A]

      while (loop) {
        q.dequeueOption match {
          case Some((a, as)) =>
            if (f(a)) {
              ref.unsafeSet(journal, as)
              res = a
              loop = false
            } else {
              q = as
            }
          case None =>
            loop = false
        }
      }

      res match {
        case null => TExit.Retry
        case a    => TExit.Succeed(a)
      }
    })

  /**
   * Returns the number of elements currently in the queue.
   */
  def size: USTM[Int] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)
      TExit.Succeed(q.length)
    })

  /**
   * Takes a single element from the queue, retrying if the queue is empty.
   */
  def take: USTM[A] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)

      q.dequeueOption match {
        case Some((a, as)) =>
          ref.unsafeSet(journal, as)
          TExit.Succeed(a)
        case None =>
          TExit.Retry
      }
    })

  /**
   * Takes all elements from the queue.
   */
  def takeAll: USTM[List[A]] =
    new ZSTM((journal, _, _, _) => {
      val q = ref.unsafeGet(journal)
      ref.unsafeSet(journal, ScalaQueue.empty[A])
      TExit.Succeed(q.toList)
    })

  /**
   * Takes up to the specified maximum number of elements from the queue.
   */
  def takeUpTo(max: Int): USTM[List[A]] =
    new ZSTM((journal, _, _, _) => {
      val q     = ref.unsafeGet(journal)
      val split = q.splitAt(max)

      ref.unsafeSet(journal, split._2)
      TExit.Succeed(split._1.toList)
    })
}

object TQueue {

  /**
   * Constructs a new bounded queue with the specified capacity.
   */
  def bounded[A](capacity: Int): USTM[TQueue[A]] =
    TRef.make(ScalaQueue.empty[A]).map(ref => new TQueue(capacity, ref))

  /**
   * Constructs a new unbounded queue.
   */
  def unbounded[A]: USTM[TQueue[A]] =
    bounded(Int.MaxValue)
}
