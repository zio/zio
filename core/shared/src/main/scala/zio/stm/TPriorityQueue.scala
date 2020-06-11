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

import zio.Chunk

/**
 * A simple `TPriorityQueue` implementation. A `TPriorityQueue` contains values
 * of type `V`. Each value is associated with a key of type `K` that an
 * `Ordering` is defined on. Unlike a `TQueue`, `take` returns the highest
 * priority value (the value that is first in the specified ordering) as
 * opposed to the first value offered to the queue. The ordering that elements
 * with the same priority will be taken from the queue is not guaranteed.
 */
final class TPriorityQueue[A] private (private val map: TMap[Int, A], private val ord: Ordering[A]) {

  /**
   * Offers the specified value to the queue with the specified priority.
   */
  def offer(a: A): USTM[Unit] =
    map.size.flatMap(n => map.put(n, a) *> bubbleUp(n))

  /**
   * Offers all of the elements in the specified collection to the queue.
   */
  def offerAll(values: Iterable[A]): USTM[Unit] =
    ZSTM.foreach_(values)(offer)

  /**
   * Peeks at the first value in the queue without removing it, retrying until
   * a value is in the queue.
   */
  def peek: USTM[A] =
    peekOption.collect { case Some(a) => a }

  /**
   * Peeks at the first value in the queue without removing it, returning
   * `None` if there is not a value in the queue.
   */
  def peekOption: USTM[Option[A]] =
    map.get(0)

  /**
   * Returns the size of the queue.
   */
  def size: USTM[Int] =
    map.size

  /**
   * Takes a value from the queue, retrying until a value is in the queue.
   */
  def take: USTM[A] =
    takeOption.collect { case Some(a) => a }

  /**
   * Takes all values from the queue.
   */
  def takeAll: USTM[List[A]] =
    map.size.flatMap(n => STM.collectAll(STM.replicate(n)(take)))

  /**
   * Takes a value from the queue, returning `None` if there is not a value in
   * the queue.
   */
  def takeOption: USTM[Option[A]] =
    map.get(0).flatMap {
      case None => STM.succeed(None)
      case Some(v) =>
        for {
          size <- map.size
          a    <- map.get(size - 1)
          _    <- map.delete(size - 1)
          _    <- map.put(0, a.get)
          _    <- bubbleDown(0)
        } yield Some(v)
    }

  /**
   * Collects all values into a chunk.
   */
  def toChunk: USTM[Chunk[A]] =
    takeAll.map(Chunk.fromIterable)

  /**
   * Collects all values into a list.
   */
  def toList: USTM[List[A]] =
    takeAll

  /**
   * Collects all values into a vector.
   */
  def toVector: USTM[Vector[A]] =
    takeAll.map(_.toVector)

  private def parent0(n: Int): Int =
    if (n == 0) 0
    else (n - 1) / 2

  private def bubbleUp(n: Int): USTM[Unit] =
    for {
      child  <- map.get(n)
      parent <- map.get(parent0(n))
      _      <- if (ord.gteq(child.get, parent.get)) STM.unit else swap(n, parent0(n)) *> bubbleUp(parent0(n))
    } yield ()

  private def bubbleDown(n: Int): USTM[Unit] =
    size.flatMap { size =>
      if (2 * n + 1 >= size) STM.unit
      else if (2 * n + 2 == size)
        for {
          x <- map.get(n)
          y <- map.get(2 * n + 1)
          _ <- if (ord.gt(x.get, y.get)) swap(n, 2 * n + 1) *> bubbleDown(2 * n + 1) else STM.unit
        } yield ()
      else {
        for {
          parent     <- map.get(n)
          leftChild  <- map.get(2 * n + 1)
          rightChild <- map.get(2 * n + 2)
          _ <- if (ord.lteq(parent.get, leftChild.get) && ord.lteq(parent.get, rightChild.get)) STM.unit
              else if (ord.lteq(leftChild.get, rightChild.get))
                swap(n, 2 * n + 1) *> bubbleDown(2 * n + 1)
              else swap(n, 2 * n + 2) *> bubbleDown(2 * n + 2)
        } yield ()
      }
    }

  private def swap(i: Int, j: Int): USTM[Unit] =
    for {
      x <- map.get(i)
      y <- map.get(j)
      _ <- map.put(i, y.get)
      _ <- map.put(j, x.get)
    } yield ()
}

object TPriorityQueue {

  /**
   * Constructs a new empty `TPriorityQueue` with the specified `Ordering`.
   */
  def empty[A](implicit ord: Ordering[A]): USTM[TPriorityQueue[A]] =
    TMap.empty[Int, A].map(map => new TPriorityQueue(map, ord))

  /**
   * Makes a new `TPriorityQueue` initialized with provided iterable.
   */
  def fromIterable[A](data: Iterable[A])(implicit ord: Ordering[A]): USTM[TPriorityQueue[A]] =
    empty[A].flatMap(queue => queue.offerAll(data).as(queue))

  /**
   * Makes a new `TPriorityQueue` that is initialized with specified values.
   */
  def make[A](data: A*)(implicit ord: Ordering[A]): USTM[TPriorityQueue[A]] =
    fromIterable(data)
}
