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

import scala.collection.immutable.{Queue => ScalaQueue}

final class TQueue[A] private (val capacity: Int, ref: TRef[ScalaQueue[A]]) {

  /**
   * Checks if the queue is empty.
   */
  def isEmpty: USTM[Boolean] =
    ZSTM.Effect((journal, _, _) => ref.unsafeGet(journal).isEmpty)

  /**
   * Checks if the queue is at capacity.
   */
  def isFull: USTM[Boolean] =
    ZSTM.Effect((journal, _, _) => ref.unsafeGet(journal).size == capacity)

  /**
   * Views the last element inserted into the queue, retrying if the queue is
   * empty.
   */
  def last: USTM[A] =
    ZSTM.Effect { (journal, _, _) =>
      ref.unsafeGet(journal).lastOption match {
        case Some(a) => a
        case None    => throw ZSTM.RetryException
      }
    }

  /**
   * Offers the specified value to the queue, retrying if the queue is at
   * capacity.
   */
  def offer(a: A): USTM[Unit] =
    ZSTM.Effect { (journal, _, _) =>
      val q = ref.unsafeGet(journal)

      if (q.length < capacity)
        ref.unsafeSet(journal, q.enqueue(a))
      else
        throw ZSTM.RetryException
    }

  /**
   * Offers each of the elements in the specified collection to the queue up to
   * the maximum capacity of the queue, retrying if there is not capacity in
   * the queue for all of these elements. Returns any remaining elements in the
   * specified collection.
   */
  def offerAll(as: Iterable[A]): USTM[Iterable[A]] =
    ZSTM.Effect { (journal, _, _) =>
      val (forQueue, remaining) = as.splitAt(capacity)

      val q = ref.unsafeGet(journal)

      if (forQueue.size > capacity - q.length) throw ZSTM.RetryException
      else {
        ref.unsafeSet(journal, q ++ forQueue)
        remaining
      }
    }

  /**
   * Views the next element in the queue without removing it, retrying if the
   * queue is empty.
   */
  def peek: USTM[A] =
    ZSTM.Effect { (journal, _, _) =>
      ref.unsafeGet(journal).headOption match {
        case Some(a) => a
        case None    => throw ZSTM.RetryException
      }
    }

  /**
   * Views the next element in the queue without removing it, returning `None`
   * if the queue is empty.
   */
  def peekOption: USTM[Option[A]] =
    ZSTM.Effect((journal, _, _) => ref.unsafeGet(journal).headOption)

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
    ZSTM.Effect { (journal, _, _) =>
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
        case null => throw ZSTM.RetryException
        case a    => a
      }
    }

  /**
   * Returns the number of elements currently in the queue.
   */
  def size: USTM[Int] =
    ZSTM.Effect((journal, _, _) => ref.unsafeGet(journal).length)

  /**
   * Takes a single element from the queue, retrying if the queue is empty.
   */
  def take: USTM[A] =
    ZSTM.Effect { (journal, _, _) =>
      ref.unsafeGet(journal).dequeueOption match {
        case Some((a, as)) =>
          ref.unsafeSet(journal, as)
          a
        case None =>
          throw ZSTM.RetryException
      }
    }

  /**
   * Takes all elements from the queue.
   */
  def takeAll: USTM[List[A]] =
    ZSTM.Effect { (journal, _, _) =>
      val q = ref.unsafeGet(journal)
      ref.unsafeSet(journal, ScalaQueue.empty[A])
      q.toList
    }

  /**
   * Takes up to the specified maximum number of elements from the queue.
   */
  def takeUpTo(max: Int): USTM[List[A]] =
    ZSTM.Effect { (journal, _, _) =>
      val split = ref.unsafeGet(journal).splitAt(max)

      ref.unsafeSet(journal, split._2)
      split._1.toList
    }
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
