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

import scala.collection.immutable.SortedMap

/**
 * A simple `TPriorityQueue` implementation. A `TPriorityQueue` contains values
 * of type `V`. Each value is associated with a key of type `K` that an
 * `Ordering` is defined on. Unlike a `TQueue`, `take` returns the highest
 * priority value (the value that is first in the specified ordering) as
 * opposed to the first value offered to the queue. The ordering that elements
 * with the same priority will be taken from the queue is not guaranteed.
 */
final class TPriorityQueue[K, V] private (private val tref: TRef[SortedMap[K, ::[V]]]) extends AnyVal {

  /**
   * Offers the specified value to the queue with the specified priority.
   */
  def offer(key: K, value: V): USTM[Unit] =
    tref.update { map =>
      map.get(key) match {
        case None         => map + (key -> ::(value, Nil))
        case Some(values) => map + (key -> ::(value, values))
      }
    }

  /**
   * Offers all of the elements in the specified collection to the queue.
   */
  def offerAll(values: Iterable[(K, V)]): USTM[Unit] =
    ZSTM.foreach_(values) { case (key, value) => offer(key, value) }

  /**
   * Peeks at the first value in the queue without removing it, retrying until
   * a value is in the queue.
   */
  def peek: USTM[V] =
    tref.get.flatMap { map =>
      map.headOption match {
        case None                  => ZSTM.retry
        case Some((_, value :: _)) => ZSTM.succeedNow(value)
      }
    }

  /**
   * Peeks at the first value in the queue without removing it, returning
   * `None` if there is not a value in the queue.
   */
  def peekOption: USTM[Option[V]] =
    tref.get.map { map =>
      map.headOption match {
        case None                  => None
        case Some((_, value :: _)) => Some(value)
      }
    }

  /**
   * Returns the size of the queue.
   */
  def size: USTM[Int] =
    tref.get.map(_.values.map(_.length).sum)

  /**
   * Takes a value from the queue, retrying until a value is in the queue.
   */
  def take: USTM[V] =
    tref.get.flatMap { map =>
      map.headOption match {
        case None                                      => ZSTM.retry
        case Some((key, value :: (values @ ::(_, _)))) => tref.update(_ + (key -> values)).as(value)
        case Some((key, value :: _))                   => tref.update(_ - key).as(value)
      }
    }

  /**
   * Takes all values from the queue.
   */
  def takeAll: USTM[List[V]] =
    tref.modify(map => (map.values.flatten.toList, SortedMap.empty[K, ::[V]](map.ordering)))

  /**
   * Takes a value from the queue, returning `None` if there is not a value in
   * the queue.
   */
  def takeOption: USTM[Option[V]] =
    tref.get.flatMap { map =>
      map.headOption match {
        case None                                    => ZSTM.none
        case Some((key, value :: Nil))               => tref.update(_ - key).as(Some(value))
        case Some((key, value :: values :: values1)) => tref.update(_ + (key -> ::(values, values1))).as(Some(value))
      }
    }

  /**
   * Collects all values into a list.
   */
  def toList: USTM[List[V]] =
    tref.get.map(map => map.values.flatten.toList)
}

object TPriorityQueue {

  /**
   * Constructs a new empty `TPriorityQueue` with the specified `Ordering`.
   */
  def empty[K, V](implicit ord: Ordering[K]): USTM[TPriorityQueue[K, V]] =
    TRef.make(SortedMap.empty[K, ::[V]]).map(tref => new TPriorityQueue(tref))
}
