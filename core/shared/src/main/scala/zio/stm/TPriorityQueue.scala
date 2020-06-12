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

import zio.Chunk

/**
 * A simple `TPriorityQueue` implementation. A `TPriorityQueue` contains values
 * of type `A` that an `Ordering` is defined on. Unlike a `TQueue`, `take`
 * returns the highest priority value (the value that is first in the specified
 * ordering) as opposed to the first value offered to the queue. The ordering
 * that elements with the same priority will be taken from the queue is not
 * guaranteed.
 */
final class TPriorityQueue[A] private (private val ref: TRef[SortedMap[A, Int]]) extends AnyVal {

  /**
   * Offers the specified value to the queue.
   */
  def offer(a: A): USTM[Unit] =
    ref.update(map => map + (a -> map.get(a).fold(1)(_ + 1)))

  /**
   * Offers all of the elements in the specified collection to the queue.
   */
  def offerAll(values: Iterable[A]): USTM[Unit] =
    ref.update(map => values.foldLeft(map)((map, a) => map + (a -> map.get(a).fold(1)(_ + 1))))

  /**
   * Peeks at the first value in the queue without removing it, retrying until
   * a value is in the queue.
   */
  def peek: USTM[A] =
    peekOption.get.orElse(ZSTM.retry)

  /**
   * Peeks at the first value in the queue without removing it, returning
   * `None` if there is not a value in the queue.
   */
  def peekOption: USTM[Option[A]] =
    ref.get.map(_.headOption.map(_._1))

  /**
   * Removes all elements from the queue matching the specified predicate.
   */
  def removeIf(f: A => Boolean): USTM[Unit] =
    retainIf(!f(_))

  /**
   * Retains only elements from the queue matching the specified predicate.
   */
  def retainIf(f: A => Boolean): USTM[Unit] =
    ref.update(_.filter { case (a, _) => f(a) })

  /**
   * Returns the size of the queue.
   */
  def size: USTM[Int] =
    ref.get.map(_.values.sum)

  /**
   * Takes a value from the queue, retrying until a value is in the queue.
   */
  def take: USTM[A] =
    takeOption.get.orElse(STM.retry)

  /**
   * Takes all values from the queue.
   */
  def takeAll: USTM[List[A]] =
    ref.modify(map => (map.flatMap { case (k, v) => List.fill(v)(k) }.toList, map.empty))

  /**
   * Takes a value from the queue, returning `None` if there is not a value in
   * the queue.
   */
  def takeOption: USTM[Option[A]] =
    ref.get.flatMap { map =>
      map.headOption match {
        case None         => STM.succeed(None)
        case Some((a, n)) => ref.update(map => if (n == 1) map - a else map + (a -> (n - 1))).as(Some(a))
      }
    }

  /**
   * Collects all values into a chunk.
   */
  def toChunk: USTM[Chunk[A]] =
    ref.get.map(map => Chunk.fromIterable(map.flatMap { case (a, n) => Chunk.fill(n)(a) }))

  /**
   * Collects all values into a list.
   */
  def toList: USTM[List[A]] =
    ref.get.map(_.flatMap { case (a, n) => List.fill(n)(a) }.toList)

  /**
   * Collects all values into a vector.
   */
  def toVector: USTM[Vector[A]] =
    ref.get.map(_.flatMap { case (a, n) => Vector.fill(n)(a) }.toVector)
}

object TPriorityQueue {

  /**
   * Constructs a new empty `TPriorityQueue` with the specified `Ordering`.
   */
  def empty[A](implicit ord: Ordering[A]): USTM[TPriorityQueue[A]] =
    TRef.make(SortedMap.empty[A, Int]).map(ref => new TPriorityQueue(ref))

  /**
   * Makes a new `TPriorityQueue` initialized with provided iterable.
   */
  def fromIterable[A](data: Iterable[A])(implicit ord: Ordering[A]): USTM[TPriorityQueue[A]] =
    TRef
      .make(data.foldLeft(SortedMap.empty[A, Int])((map, a) => map + (a -> map.get(a).fold(1)(_ + 1))))
      .map(ref => new TPriorityQueue(ref))

  /**
   * Makes a new `TPriorityQueue` that is initialized with specified values.
   */
  def make[A](data: A*)(implicit ord: Ordering[A]): USTM[TPriorityQueue[A]] =
    fromIterable(data)
}
