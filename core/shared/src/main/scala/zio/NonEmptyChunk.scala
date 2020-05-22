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

package zio

import zio.NonemptyChunkModule.NonEmptyChunk.nonEmpty
import zio.NonemptyChunkModule.{ newtype, NonEmptyChunk }

/**
 * A `NonEmptyChunk` is a `Chunk` that is guaranteed to contain at least one
 * element. As a result, operations which would not be safe when performed on
 * `Chunk`, such as `head` or `reduce`, are safe when performed on
 * `NonEmptyChunk`. Operations on `NonEmptyChunk` which could potentially
 * return an empty chunk will return a `Chunk` instead.
 */
private[zio] trait NonEmpty[F[+_]] {
  type Type[+A] //<: F[A]

  def wrap[A](chunk: F[A]): Type[A]
  def unwrap[A](chunk: Type[A]): F[A]
}

private[zio] trait LowPriorityChunkImplicit {
  import scala.language.implicitConversions
  implicit def toChunk0[A](c: NonEmptyChunk[A]): Chunk[A] = newtype.unwrap(c)
}

object NonemptyChunkModule extends LowPriorityChunkImplicit {

  private[zio] val newtype: NonEmpty[Chunk] =
    new NonEmpty[Chunk] {
      type Type[+A] = Chunk[A]

      def wrap[A](chunk: Chunk[A]): Type[A] =
        chunk

      def unwrap[A](chunk: Chunk[A]): Chunk[A] =
        chunk
    }

  /**
   * Constructs a `NonEmptyChunk` from a `Chunk`. This should only be used
   * when it is statically known that the `Chunk` must have at least one
   * element.
   */
  type NonEmptyChunk[+A] = newtype.Type[A]

  object NonEmptyChunk {

    private[zio] def nonEmpty[A](chunk: Chunk[A]): NonEmptyChunk[A] =
      newtype.wrap(chunk)

    def apply[A](a: A, as: Chunk[A]): NonEmptyChunk[A] =
      nonEmpty(Chunk.single(a) ++ as)

    def apply[A](a: A): NonEmptyChunk[A] =
      nonEmpty(Chunk.single(a))

    /**
     * Checks if a `chunk` is not empty and constructs a `NonEmptyChunk` from it.
     */
    def fromChunk[A](chunk: Chunk[A]): Option[NonEmptyChunk[A]] =
      fold[A, Option[NonEmptyChunk[A]]](chunk)(None)(Some(_))

    /**
     * Constructs a `NonEmptyChunk` from the `::` case of a `List`.
     */
    def fromCons[A](as: ::[A]): NonEmptyChunk[A] =
      as match {
        case h :: t => fromIterable(h, t)
      }

    /**
     * Constructs a `NonEmptyChunk` from an `Iterable`.
     */
    def fromIterable[A](a: A, as: Iterable[A]): NonEmptyChunk[A] =
      nonEmpty(Chunk.single(a) ++ Chunk.fromIterable(as))

    def fold[A, B](chunk: Chunk[A])(ifEmpty: => B)(fn: NonEmptyChunk[A] => B): B =
      if (chunk.isEmpty) ifEmpty else fn(nonEmpty(chunk))

    /**
     * Constructs a `NonEmptyChunk` from a single value.
     */
    def single[A](a: A): NonEmptyChunk[A] =
      nonEmpty(Chunk.single(a))

  }

  implicit class NonEmptyChunkSyntax[+A](private val self: NonEmptyChunk[A]) extends AnyVal {
    @inline def chunk: Chunk[A] = self.asInstanceOf[Chunk[A]]

    /**
     * Apparents a single element to the end of this `NonEmptyChunk`.
     */
    def :+[A1 >: A](a: A1): NonEmptyChunk[A1] =
      nonEmpty(chunk :+ a)

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
     * Converts this `NonEmptyChunk` of bytes to a `NonEmptyChunk` of bits.
     */
    def asBits(implicit ev: A <:< Byte): NonEmptyChunk[Boolean] =
      nonEmpty(chunk.asBits)

    /**
     * Maps each element of this `NonEmptyChunk` to a new `NonEmptyChunk` and
     * then concatenates them together.
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
      chunk.mapAccum(s)(f) match {
        case (s, chunk) => (s, nonEmpty(chunk))
      }

    /**
     * Effectfully maps over the elements of this `NonEmptyChunk`, maintaining
     * some state along the way.
     */
    def mapAccumM[R, E, S, B](s: S)(f: (S, A) => ZIO[R, E, (S, B)]): ZIO[R, E, (S, NonEmptyChunk[B])] =
      chunk.mapAccumM(s)(f).map { case (s, chunk) => (s, nonEmpty(chunk)) }

    /**
     * Effectfully maps the elements of this `NonEmptyChunk`.
     */
    def mapM[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, NonEmptyChunk[B]] =
      chunk.mapM(f).map(nonEmpty)

    /**
     * Effectfully maps the elements of this `NonEmptyChunk` in parallel.
     */
    def mapMPar[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, NonEmptyChunk[B]] =
      chunk.mapMPar(f).map(nonEmpty)

    /**
     * Materialize the elements of this `NonEmptyChunk` into a `NonEmptyChunk`
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
     * Converts this `NonEmptyChunk` to a `Chunk`, discarding information about
     * it not being empty.
     */
    def toChunk: Chunk[A] =
      chunk

    /**
     * Converts this `NonEmptyChunk` to the `::` case of a `List`.
     */
    def toCons[A1 >: A]: ::[A1] =
      ::(chunk(0), chunk.drop(1).toList)

    /**
     * Renders this `NonEmptyChunk` as a `String`.
     */
    override def toString: String =
      chunk.mkString("NonEmptyChunk(", ", ", ")")

    /**
     * Zips this `NonEmptyChunk` with the specified `Chunk`, using the specified
     * functions to "fill in" missing values if one chunk has fewer elements
     * than the other.
     */
    def zipAllWith[B, C](
      that: Chunk[B]
    )(left: A => C, right: B => C)(both: (A, B) => C): NonEmptyChunk[C] =
      nonEmpty(chunk.zipAllWith(that)(left, right)(both))

    /**
     * Zips this `NonEmptyCHunk` with the specified `NonEmptyChunk`, only
     * keeping as many elements as are in the smaller chunk.
     */
    final def zipWith[B, C](that: NonEmptyChunk[B])(f: (A, B) => C): NonEmptyChunk[C] =
      nonEmpty(chunk.zipWith(that.chunk)(f))

    /**
     * Annotates each element of this `NonEmptyChunk` with its index.
     */
    def zipWithIndex: NonEmptyChunk[(A, Int)] =
      nonEmpty(chunk.zipWithIndex)

    /**
     * Annotates each element of this `NonEmptyChunk` with its index, with the
     * specified offset.
     */
    final def zipWithIndexFrom(indexOffset: Int): NonEmptyChunk[(A, Int)] =
      nonEmpty(chunk.zipWithIndexFrom(indexOffset))
  }

}
