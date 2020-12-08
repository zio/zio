/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.poly

import zio.random.Random
import zio.test.{ Gen, Sized }

import scala.annotation.tailrec

/**
 * `GenOrderingPoly` provides evidence that instances of `Gen[T]` and
 * `Ordering[T]` exist for some concrete but unknown type `T`.
 */
trait GenOrderingPoly extends GenPoly {
  val ordT: Ordering[T]
}

object GenOrderingPoly {

  /**
   * Constructs an instance of `GenOrderingPoly` using the specified `Gen` and
   * `Ordering` instances, existentially hiding the underlying type.
   */
  def apply[A](gen: Gen[Random with Sized, A], ord: Ordering[A]): GenOrderingPoly =
    new GenOrderingPoly {
      type T = A
      val genT = gen
      val ordT = ord
    }

  /**
   * Provides evidence that instances of `Gen` and a `Ordering` exist for
   * booleans.
   */
  val boolean: GenOrderingPoly =
    GenOrderingPoly(Gen.boolean, Ordering.Boolean)

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for bytes.
   */
  val byte: GenOrderingPoly =
    GenNumericPoly.byte

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for
   * characters.
   */
  val char: GenOrderingPoly =
    GenNumericPoly.char

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for doubles.
   */
  val double: GenOrderingPoly =
    GenNumericPoly.double

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for floats.
   */
  val float: GenOrderingPoly =
    GenNumericPoly.float

  lazy val genOrderingPoly: Gen[Random, GenOrderingPoly] = {
    val primitives = Gen.elements(
      boolean,
      byte,
      char,
      int,
      double,
      float,
      long,
      short,
      string,
      unit
    )
    val constructors = Gen.elements(list _, option _, vector _)
    val collections = for {
      constructor <- constructors
      primitive   <- primitives
    } yield constructor(primitive)
    Gen.weighted(primitives -> 10, collections -> 3)
  }

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for
   * integers.
   */
  val int: GenOrderingPoly =
    GenNumericPoly.int

  /**
   * Provides evidence that instances of `Gen[List[T]]` and
   * `Ordering[List[T]]` exist for any type for which `Gen[T]` and
   * `Ordering[T]` exist.
   */
  def list(poly: GenOrderingPoly): GenOrderingPoly =
    GenOrderingPoly(Gen.listOf(poly.genT), ListOrdering(poly.ordT))

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for longs.
   */
  val long: GenOrderingPoly =
    GenNumericPoly.long

  /**
   * Provides evidence that instances of `Gen[Option[T]]` and
   * `Ordering[Option[T]]` exist for any type for which `Gen[T]` and
   * `Ordering[T]` exist.
   */
  def option(poly: GenOrderingPoly): GenOrderingPoly =
    GenOrderingPoly(Gen.option(poly.genT), Ordering.Option(poly.ordT))

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for shorts.
   */
  val short: GenOrderingPoly =
    GenNumericPoly.long

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for
   * strings.
   */
  val string: GenOrderingPoly =
    GenOrderingPoly(Gen.anyString, Ordering.String)

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for
   * the unit value.
   */
  val unit: GenOrderingPoly =
    GenOrderingPoly(Gen.unit, Ordering.Unit)

  /**
   * Provides evidence that instances of `Gen[Vector[T]]` and
   * `Ordering[Vector[T]]` exist for any type for which `Gen[T]` and
   * `Ordering[T]` exist.
   */
  def vector(poly: GenOrderingPoly): GenOrderingPoly =
    GenOrderingPoly(Gen.vectorOf(poly.genT), VectorOrdering(poly.ordT))

  /**
   * Derives an `Ordering[List[A]]` given an `Ordering[A]`.
   */
  private implicit def ListOrdering[A: Ordering]: Ordering[List[A]] = {

    @tailrec
    def loop[A: Ordering](left: List[A], right: List[A]): Int =
      (left, right) match {
        case (Nil, Nil) => 0
        case (Nil, _)   => -1
        case (_, Nil)   => +1
        case ((h1 :: t1), (h2 :: t2)) =>
          val compare = Ordering[A].compare(h1, h2)
          if (compare == 0) loop(t1, t2) else compare
      }

    (l, r) => loop(l, r)
  }

  /**
   * Derives an `Ordering[Vector[A]]` given an `Ordering[A]`.
   */
  private implicit def VectorOrdering[A: Ordering]: Ordering[Vector[A]] =
    (l, r) => {
      val j = l.length
      val k = r.length

      def loop(i: Int): Int =
        if (i == j && i == k) 0
        else if (i == j) -1
        else if (i == k) +1
        else {
          val compare = Ordering[A].compare(l(i), r(i))
          if (compare == 0) loop(i + 1) else compare
        }

      loop(0)
    }
}
