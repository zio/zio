/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import zio.NonEmptyChunk._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * A `NonEmptyChunk` is a `Chunk` that contains at least one element. As a
 * result, operations that throw on an empty `Chunk` because it has no elements,
 * such as `head` or `reduce`, do not throw for that reason on a
 * `NonEmptyChunk`. Operations that can drop elements, such as `filter`,
 * `collect`, `init` and `tail`, return a `Chunk` instead, and an implicit
 * conversion turns a `NonEmptyChunk` into a `Chunk` wherever a `Chunk` is
 * expected.
 *
 * {{{
 * val numbers: NonEmptyChunk[Int] = NonEmptyChunk(1, 2, 3)
 *
 * val first: Int = numbers.head
 * // 1
 * val doubled: NonEmptyChunk[Int] = numbers.map(_ * 2)
 * // NonEmptyChunk(2, 4, 6)
 * val plain: Chunk[Int] = doubled.toChunk
 * // Chunk(2,4,6)
 *
 * val none: Option[NonEmptyChunk[Int]] =
 *   NonEmptyChunk.fromChunk(Chunk.empty[Int])
 * // None
 * val some: Option[NonEmptyChunk[Int]] =
 *   NonEmptyChunk.fromChunk(Chunk(4, 5))
 * // Some(NonEmptyChunk(4, 5))
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/stream/chunk/ Chunk]]
 */
final class NonEmptyChunk[+A] private (private val chunk: Chunk[A])
    extends NonEmptySeq[A, NonEmptyChunk, Chunk]
    with Serializable { self =>

  /**
   * Prepends a single element to the beginning of this `NonEmptyChunk`. A
   * symbolic alias for `prepended`.
   */
  def +:[A1 >: A](a: A1): NonEmptyChunk[A1] =
    prepended(a)

  /**
   * Appends a single element to the end of this `NonEmptyChunk`. A symbolic
   * alias for `appended`.
   */
  def :+[A1 >: A](a: A1): NonEmptyChunk[A1] =
    appended(a)

  /**
   * Appends the specified `Chunk` to the end of this `NonEmptyChunk`.
   */
  def ++[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1] =
    append(that)

  /**
   * A named alias for `++`.
   */
  def append[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1] =
    nonEmpty(chunk ++ that)

  /**
   * Appends a single element to the end of this `NonEmptyChunk`.
   */
  override def appended[A1 >: A](a: A1): NonEmptyChunk[A1] =
    nonEmpty(chunk :+ a)

  /**
   * Converts this `NonEmptyChunk` of ints to a `NonEmptyChunk` of bits, 32 per
   * int, in the bit order given by `endianness`.
   */
  def asBitsInt(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Int): NonEmptyChunk[Boolean] =
    nonEmpty(chunk.asBitsInt(endianness))

  /**
   * Converts this `NonEmptyChunk` of longs to a `NonEmptyChunk` of bits, 64 per
   * long, in the bit order given by `endianness`.
   */
  def asBitsLong(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Long): NonEmptyChunk[Boolean] =
    nonEmpty(chunk.asBitsLong(endianness))

  /**
   * Converts this `NonEmptyChunk` of bytes to a `NonEmptyChunk` of bits, 8 per
   * byte, most significant bit first.
   */
  def asBits(implicit ev: A <:< Byte): NonEmptyChunk[Boolean] =
    nonEmpty(chunk.asBitsByte)

  /**
   * Returns the results of `pf` for the elements where it is defined, as a
   * `Chunk`, which can be empty. Elements for which `pf` returns `null` are
   * left out of the result.
   */
  override def collect[B](pf: PartialFunction[A, B]): Chunk[B] =
    chunk.collect(pf)

  /**
   * Returns the result of `pf` for the first element where it is defined, or
   * `None` when it is defined for no element.
   */
  override def collectFirst[B](pf: PartialFunction[A, B]): Option[B] =
    chunk.collectFirst(pf)

  /**
   * Returns the elements without duplicates, keeping the first occurrence of
   * each in its original position.
   */
  override def distinct: NonEmptyChunk[A] =
    nonEmpty(chunk.distinct)

  /**
   * Returns `true` when `that` is a `NonEmptyChunk` with equal elements in the
   * same order. A `NonEmptyChunk` and a `Chunk` with the same elements are not
   * equal, whichever side the comparison starts from.
   */
  override def equals(that: Any): Boolean =
    that match {
      case that: NonEmptyChunk[_] => self.chunk == that.chunk
      case _                      => false
    }

  /**
   * Determines whether a predicate is satisfied for at least one element of
   * this `NonEmptyChunk`.
   */
  override def exists(p: A => Boolean): Boolean =
    chunk.exists(p)

  /**
   * Returns the elements that satisfy `p`, as a `Chunk`, which can be empty.
   */
  override def filter(p: A => Boolean): Chunk[A] =
    chunk.filter(p)

  /**
   * Returns the elements that do not satisfy `p`, as a `Chunk`, which can be
   * empty.
   */
  override def filterNot(p: A => Boolean): Chunk[A] =
    chunk.filterNot(p)

  /**
   * Returns the first element that satisfies `p`, or `None` when no element
   * does.
   */
  override def find(p: A => Boolean): Option[A] =
    chunk.find(p)

  /**
   * Maps each element of this `NonEmptyChunk` to a new `NonEmptyChunk` and then
   * concatenates them together.
   */
  def flatMap[B](f: A => NonEmptyChunk[B]): NonEmptyChunk[B] =
    nonEmpty(chunk.flatMap(a => f(a).chunk))

  /**
   * Flattens a `NonEmptyChunk` of `NonEmptyChunk` values to a single
   * `NonEmptyChunk`.
   */
  def flatten[B](implicit ev: A <:< NonEmptyChunk[B]): NonEmptyChunk[B] =
    flatMap(ev)

  /**
   * Combines the elements from left to right with `op`, starting from `z`.
   */
  override def foldLeft[B](z: B)(op: (B, A) => B): B =
    chunk.foldLeft(z)(op)

  /**
   * Returns `true` when `p` holds for every element.
   */
  override def forall(p: A => Boolean): Boolean =
    chunk.forall(p)

  /**
   * Splits this `NonEmptyChunk` into groups of `size` elements, in order. The
   * last group holds the remaining elements and can be smaller. Throws a
   * `java.lang.IllegalArgumentException` when `size` is zero or negative.
   */
  override def grouped(size: Int): Iterator[NonEmptyChunk[A]] =
    chunk.grouped(size).map(nonEmpty)

  /**
   * Groups the values in this `NonEmptyChunk` using the specified function.
   * Each key maps to the values that produced it, in their original order.
   */
  def groupBy[K](f: A => K): Map[K, NonEmptyChunk[A]] =
    groupMap(f)(identity)

  /**
   * Groups the values in this `NonEmptyChunk` by `key` and transforms each
   * value with `f`. Each key maps to the transformed values, in their original
   * order.
   */
  def groupMap[K, V](key: A => K)(f: A => V): Map[K, NonEmptyChunk[V]] = {
    val m = collection.mutable.Map.empty[K, ChunkBuilder[V]]
    for (elem <- chunk) {
      val k       = key(elem)
      val builder = m.getOrElseUpdate(k, ChunkBuilder.make[V]())
      builder += f(elem)
    }
    var result = collection.immutable.Map.empty[K, NonEmptyChunk[V]]
    m.foreach { case (k, v) =>
      result = result + ((k, nonEmpty(v.result())))
    }
    result
  }

  /**
   * Returns the hashcode of this `NonEmptyChunk`, which is the hashcode of the
   * underlying `Chunk`.
   */
  override def hashCode: Int =
    chunk.hashCode

  /**
   * Returns the first element.
   */
  override def head: A =
    chunk.head

  /**
   * Returns every element except the last, as a `Chunk`, which is empty when
   * this `NonEmptyChunk` has one element.
   */
  override def init: Chunk[A] =
    chunk.init

  /**
   * Returns an iterator over the elements.
   */
  override def iterator: Iterator[A] =
    chunk.iterator

  /**
   * Returns the last element.
   */
  override def last: A =
    chunk.last

  /**
   * Transforms the elements of this `NonEmptyChunk` with the specified
   * function.
   */
  def map[B](f: A => B): NonEmptyChunk[B] =
    nonEmpty(chunk.map(f))

  /**
   * Maps over the elements of this `NonEmptyChunk`, maintaining some state
   * along the way.
   */
  def mapAccum[S, B](s: S)(f: (S, A) => (S, B)): (S, NonEmptyChunk[B]) =
    chunk.mapAccum(s)(f) match { case (s, chunk) => (s, nonEmpty(chunk)) }

  /**
   * Effectfully maps over the elements of this `NonEmptyChunk`, maintaining
   * some state along the way.
   */
  def mapAccumZIO[R, E, S, B](s: S)(f: (S, A) => ZIO[R, E, (S, B)])(implicit
    trace: Trace
  ): ZIO[R, E, (S, NonEmptyChunk[B])] =
    chunk.mapAccumZIO(s)(f).map { case (s, chunk) => (s, nonEmpty(chunk)) }

  /**
   * Effectfully maps the elements of this `NonEmptyChunk`.
   */
  def mapZIO[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, NonEmptyChunk[B]] =
    chunk.mapZIO(f).map(nonEmpty)

  /**
   * Effectfully maps the elements of this `NonEmptyChunk` in parallel.
   */
  def mapZIOPar[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, NonEmptyChunk[B]] =
    chunk.mapZIOPar(f).map(nonEmpty)

  /**
   * Materializes the elements of this `NonEmptyChunk` into a `NonEmptyChunk`
   * backed by an array.
   */
  def materialize[A1 >: A]: NonEmptyChunk[A1] =
    nonEmpty(chunk.materialize)

  /**
   * Prepends the specified `Chunk` to the beginning of this `NonEmptyChunk`.
   */
  def prepend[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1] =
    nonEmpty(that ++ chunk)

  /**
   * Prepends a single element to the beginning of this `NonEmptyChunk`.
   */
  def prepended[A1 >: A](a: A1): NonEmptyChunk[A1] =
    nonEmpty(a +: chunk)

  /**
   * Combines the elements from left to right with `op`. A `NonEmptyChunk` with
   * one element returns that element.
   */
  override def reduce[B >: A](op: (B, B) => B): B =
    chunk.reduce(op)

  /**
   * Reduces the elements of this `NonEmptyChunk` from left to right using the
   * function `map` to transform the first value to the type `B` and then the
   * function `reduce` to combine the `B` value with each other `A` value.
   *
   * When `map` or `reduce` returns `null`, the next element goes to `map` again
   * instead of to `reduce`.
   *
   * {{{
   * val joined: String =
   *   NonEmptyChunk(1, 2, 3).reduceMapLeft(_.toString)(_ + "-" + _)
   * // "1-2-3"
   * }}}
   */
  def reduceMapLeft[B](map: A => B)(reduce: (B, A) => B): B = {
    val iterator = chunk.iterator
    var b: B     = null.asInstanceOf[B]
    while (iterator.hasNext) {
      val a = iterator.next()
      if (b == null) b = map(a) else b = reduce(b, a)
    }
    b
  }

  /**
   * Reduces the elements of this `NonEmptyChunk` from right to left using the
   * function `map` to transform the last value to the type `B` and then the
   * function `reduce` to combine each earlier `A` value with the `B` value.
   *
   * When `map` or `reduce` returns `null`, the next element goes to `map` again
   * instead of to `reduce`.
   */
  def reduceMapRight[B](map: A => B)(reduce: (A, B) => B): B = {
    val iterator = chunk.reverseIterator
    var b: B     = null.asInstanceOf[B]
    while (iterator.hasNext) {
      val a = iterator.next()
      if (b == null) b = map(a) else b = reduce(a, b)
    }
    b
  }

  /**
   * Returns the elements in reverse order.
   */
  override def reverse: NonEmptyChunk[A] =
    nonEmpty(chunk.reverse)

  /**
   * Returns the number of elements, which is at least 1.
   */
  override def size: Int =
    chunk.size

  /**
   * Returns the elements sorted by the key that `f` computes, compared with
   * `ord`.
   */
  override def sortBy[B](f: A => B)(implicit ord: Ordering[B]): NonEmptyChunk[A] =
    nonEmpty(chunk.sortBy(f))

  /**
   * Returns the elements sorted with `ord`.
   */
  override def sorted[B >: A](implicit ord: Ordering[B]): NonEmptyChunk[B] =
    nonEmpty(chunk.sorted[B])

  /**
   * Returns every element except the first, as a `Chunk`, which is empty when
   * this `NonEmptyChunk` has one element.
   */
  override def tail: Chunk[A] =
    chunk.tail

  /**
   * Converts this `NonEmptyChunk` to an array.
   */
  override def toArray[B >: A: ClassTag]: Array[B] =
    chunk.toArray

  /**
   * Converts this `NonEmptyChunk` to a `Chunk`, discarding information about it
   * not being empty. It returns the underlying `Chunk` without copying.
   */
  def toChunk: Chunk[A] =
    chunk

  /**
   * Converts this `NonEmptyChunk` to the `::` case of a `List`.
   */
  def toCons[A1 >: A]: ::[A1] =
    ::(chunk(0), chunk.drop(1).toList)

  /**
   * Converts this `NonEmptyChunk` to an `Iterable`.
   */
  override def toIterable: Iterable[A] =
    chunk

  /**
   * Converts this `NonEmptyChunk` to a `List`.
   */
  override def toList: List[A] =
    chunk.toList

  /**
   * Renders this `NonEmptyChunk` as a `String`, such as `NonEmptyChunk(1, 2,
   * 3)`. On Scala 2.12, it throws a `java.lang.NullPointerException` when an
   * element is `null`.
   */
  override def toString: String =
    chunk.mkString("NonEmptyChunk(", ", ", ")")

  /**
   * Zips this `NonEmptyChunk` with the specified `NonEmptyChunk`, only keeping
   * as many elements as are in the smaller chunk. The `Zippable` instance
   * combines each pair, so zipping a chunk of tuples with another chunk gives
   * flat tuples: `NonEmptyChunk((1, "a")).zip(NonEmptyChunk(true))` is
   * `NonEmptyChunk((1, "a", true))`.
   */
  def zip[B](that: NonEmptyChunk[B])(implicit zippable: Zippable[A, B]): NonEmptyChunk[zippable.Out] =
    zipWith(that)(zippable.zip(_, _))

  /**
   * Zips this `NonEmptyChunk` with the specified `Chunk`, using `None` to "fill
   * in" missing values if one chunk has fewer elements than the other.
   */
  def zipAll[B](that: Chunk[B]): NonEmptyChunk[(Option[A], Option[B])] =
    zipAllWith(that)(a => (Some(a), None), b => (None, Some(b)))((a, b) => (Some(a), Some(b)))

  /**
   * Zips this `NonEmptyChunk` with the specified `Chunk`, using the specified
   * functions to "fill in" missing values if one chunk has fewer elements than
   * the other.
   */
  def zipAllWith[B, C](
    that: Chunk[B]
  )(left: A => C, right: B => C)(both: (A, B) => C): NonEmptyChunk[C] =
    nonEmpty(chunk.zipAllWith(that)(left, right)(both))

  /**
   * Zips this `NonEmptyChunk` with the specified `NonEmptyChunk`, only keeping
   * as many elements as are in the smaller chunk.
   */
  def zipWith[B, C](that: NonEmptyChunk[B])(f: (A, B) => C): NonEmptyChunk[C] =
    nonEmpty(chunk.zipWith(that.chunk)(f))

  /**
   * Annotates each element of this `NonEmptyChunk` with its index, starting
   * from 0: `NonEmptyChunk("a", "b").zipWithIndex` is `NonEmptyChunk(("a", 0),
   * ("b", 1))`.
   */
  override def zipWithIndex: NonEmptyChunk[(A, Int)] =
    nonEmpty(chunk.zipWithIndex)

  /**
   * Annotates each element of this `NonEmptyChunk` with its index, with the
   * specified offset.
   */
  final def zipWithIndexFrom(indexOffset: Int): NonEmptyChunk[(A, Int)] =
    nonEmpty(chunk.zipWithIndexFrom(indexOffset))

}

/**
 * Constructors and extractors for [[NonEmptyChunk]], and the implicit
 * conversion from a `NonEmptyChunk` to a `Chunk`.
 */
object NonEmptyChunk {

  /**
   * Constructs a `NonEmptyChunk` from one or more values.
   */
  def apply[A](a: A, as: A*): NonEmptyChunk[A] =
    fromIterable(a, as)

  /**
   * Returns a `NonEmptyChunk` with the elements of `chunk` in a `Some`, or
   * `None` when `chunk` is empty.
   */
  def fromChunk[A](chunk: Chunk[A]): Option[NonEmptyChunk[A]] =
    chunk.nonEmptyOrElse[Option[NonEmptyChunk[A]]](None)(Some(_))

  /**
   * Constructs a `NonEmptyChunk` from the `::` case of a `List`.
   */
  def fromCons[A](as: ::[A]): NonEmptyChunk[A] =
    as match { case h :: t => fromIterable(h, t) }

  /**
   * Constructs a `NonEmptyChunk` from the element `a` followed by the elements
   * of `as`. When `as` is empty, the result holds `a` alone.
   */
  def fromIterable[A](a: A, as: Iterable[A]): NonEmptyChunk[A] =
    if (as.isEmpty) single(a)
    else
      nonEmpty {
        val builder = ChunkBuilder.make[A]()
        builder.sizeHint(as, 1)
        builder += a
        builder ++= as
        builder.result()
      }

  /**
   * Returns a `NonEmptyChunk` with the elements of `as` in a `Some`, or `None`
   * when `as` is empty.
   */
  def fromIterableOption[A](as: Iterable[A]): Option[NonEmptyChunk[A]] =
    if (as.isEmpty) None else Some(nonEmpty(Chunk.fromIterable(as)))

  /**
   * Constructs a `NonEmptyChunk` from a single value.
   */
  def single[A](a: A): NonEmptyChunk[A] =
    nonEmpty(Chunk.single(a))

  /**
   * Extracts the elements from a non-empty `Chunk`. An empty `Chunk`, or a
   * `Seq` that is not a `Chunk` (such as a `Vector`), does not match.
   */
  def unapplySeq[A](seq: Seq[A]): Option[Seq[A]] =
    seq match {
      case chunk: Chunk[A] if chunk.nonEmpty => Some(chunk)
      case _                                 => None
    }

  /**
   * Extracts the elements from a `NonEmptyChunk`.
   */
  def unapplySeq[A](nonEmptyChunk: NonEmptyChunk[A]): Some[Seq[A]] =
    Some(nonEmptyChunk.chunk)

  /**
   * The `NonEmptyChunk` that holds the single value `()`.
   */
  val unit: NonEmptyChunk[Unit] = single(())

  /**
   * Provides an implicit conversion from `NonEmptyChunk` to `Chunk` for methods
   * that may not return a `NonEmptyChunk`.
   */
  implicit def toChunk[A](nonEmptyChunk: NonEmptyChunk[A]): Chunk[A] =
    nonEmptyChunk.chunk

  /**
   * Constructs a `NonEmptyChunk` from a `Chunk`. This should only be used when
   * it is statically known that the `Chunk` must have at least one element.
   */
  private[zio] def nonEmpty[A](chunk: Chunk[A]): NonEmptyChunk[A] =
    new NonEmptyChunk(chunk)
}
