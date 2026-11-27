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

package zio.test.poly

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.Gen
import zio.{Random, Trace}

import scala.annotation.tailrec

/**
 * `GenOrderingPoly` provides evidence that instances of `Gen[T]` and `Ordering[T]` exist for some concrete but unknown type `T`. Use it for properties that sort or compare generated values of a type that changes from sample to sample.
 *
 * {{{
 * import zio.test._
 *
 * val polys =
 *   Gen.elements(GenOrderingPoly.string, GenOrderingPoly.boolean, GenOrderingPoly.list(GenOrderingPoly.string))
 *
 * val sortTwice =
 *   test("sorting a sorted list keeps it unchanged") {
 *     check(polys) { poly =>
 *       check(Gen.listOf(poly.genT)) { values =>
 *         val once = values.sorted(poly.ordT)
 *         assertTrue(once.sorted(poly.ordT) == once)
 *       }
 *     }
 *   }
 * }}}
 *
 * @see [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
trait GenOrderingPoly extends GenPoly {

  /**
   * The `Ordering` of `T`. It is `null` on the instances built by `GenNumericPoly.apply`, `GenIntegralPoly.apply` and `GenFractionalPoly.apply`, which include the `byte`, `char`, `int`, `long`, `short`, `double` and `float` instances of the `GenOrderingPoly`, `GenNumericPoly`, `GenIntegralPoly` and `GenFractionalPoly` companions: on those, `numT` holds the ordering.
   */
  val ordT: Ordering[T]
}

/**
 * Constructors of [[GenOrderingPoly]] instances for primitive types and for `List`, `Option` and `Vector` of another `GenOrderingPoly`, and `genOrderingPoly`, a generator that picks among them.
 *
 * @see [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
object GenOrderingPoly {

  /**
   * Constructs an instance of `GenOrderingPoly` using the specified `Gen` and `Ordering` instances, existentially hiding the underlying type.
   */
  def apply[A](gen: Gen[Any, A], ord: Ordering[A]): GenOrderingPoly =
    new GenOrderingPoly {
      type T = A
      val genT = gen
      val ordT = ord
    }

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for booleans, using `Gen.boolean` and `Ordering.Boolean`.
   */
  def boolean(implicit trace: Trace): GenOrderingPoly =
    GenOrderingPoly(Gen.boolean, Ordering.Boolean)

  /**
   * Returns a `GenOrderingPoly` for `Byte` values, built by `GenNumericPoly.byte`, whose `ordT` is `null` and whose `numT` holds the ordering.
   */
  def byte(implicit trace: Trace): GenOrderingPoly =
    GenNumericPoly.byte

  /**
   * Returns a `GenOrderingPoly` for `Char` values, built by `GenNumericPoly.char`, whose `ordT` is `null` and whose `numT` holds the ordering.
   */
  def char(implicit trace: Trace): GenOrderingPoly =
    GenNumericPoly.char

  /**
   * Returns a `GenOrderingPoly` for `Double` values, built by `GenNumericPoly.double`, whose `ordT` is `null` and whose `numT` holds the ordering.
   */
  def double(implicit trace: Trace): GenOrderingPoly =
    GenNumericPoly.double

  /**
   * Returns a `GenOrderingPoly` for `Float` values, built by `GenNumericPoly.float`, whose `ordT` is `null` and whose `numT` holds the ordering.
   */
  def float(implicit trace: Trace): GenOrderingPoly =
    GenNumericPoly.float

  /**
   * A generator of `GenOrderingPoly` instances. With weight 10 it picks one of `boolean`, `byte`, `char`, `int`, `double`, `float`, `long`, `short`, `string` and `unit`, and with weight 3 a `list`, `option` or `vector` of one of them. The `byte`, `char`, `int`, `double`, `float`, `long` and `short` instances have a `null` `ordT` (see `ordT`), and so do not give a usable ordering on their own or inside a collection.
   */
  def genOrderingPoly(implicit trace: Trace): Gen[Any, GenOrderingPoly] = {
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
   * Returns a `GenOrderingPoly` for `Int` values, built by `GenNumericPoly.int`, whose `ordT` is `null` and whose `numT` holds the ordering.
   */
  def int(implicit trace: Trace): GenOrderingPoly =
    GenNumericPoly.int

  /**
   * Returns a `GenOrderingPoly` for lists of the values of `poly`, generated by `Gen.listOf(poly.genT)`. Its ordering compares lists element by element with `poly.ordT`, and a list that is a prefix of another comes first. When `poly.ordT` is `null`, comparing two non-empty lists throws a `java.lang.NullPointerException`.
   */
  def list(poly: GenOrderingPoly)(implicit trace: Trace): GenOrderingPoly =
    GenOrderingPoly(Gen.listOf(poly.genT), ListOrdering(poly.ordT))

  /**
   * Returns a `GenOrderingPoly` for `Long` values, built by `GenNumericPoly.long`, whose `ordT` is `null` and whose `numT` holds the ordering.
   */
  def long(implicit trace: Trace): GenOrderingPoly =
    GenNumericPoly.long

  /**
   * Returns a `GenOrderingPoly` for options of the values of `poly`, generated by `Gen.option(poly.genT)` and ordered by `Ordering.Option(poly.ordT)`, which puts `None` first. When `poly.ordT` is `null`, comparing two `Some` values throws a `java.lang.NullPointerException`.
   */
  def option(poly: GenOrderingPoly)(implicit trace: Trace): GenOrderingPoly =
    GenOrderingPoly(Gen.option(poly.genT), Ordering.Option(poly.ordT))

  /**
   * Returns a `GenOrderingPoly` whose values are `Long`s, generated by `Gen.long`: like `long`, it is built by `GenNumericPoly.long`, so its `ordT` is `null` and its `numT` holds the ordering.
   */
  def short(implicit trace: Trace): GenOrderingPoly =
    GenNumericPoly.long

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for strings, using `Gen.string` and `Ordering.String`.
   */
  def string(implicit trace: Trace): GenOrderingPoly =
    GenOrderingPoly(Gen.string, Ordering.String)

  /**
   * Provides evidence that instances of `Gen` and `Ordering` exist for the unit value, using `Gen.unit` and `Ordering.Unit`.
   */
  def unit(implicit trace: Trace): GenOrderingPoly =
    GenOrderingPoly(Gen.unit, Ordering.Unit)

  /**
   * Returns a `GenOrderingPoly` for vectors of the values of `poly`, generated by `Gen.vectorOf(poly.genT)`. Its ordering compares vectors element by element with `poly.ordT`, and a vector that is a prefix of another comes first. When `poly.ordT` is `null`, comparing two non-empty vectors throws a `java.lang.NullPointerException`.
   */
  def vector(poly: GenOrderingPoly)(implicit trace: Trace): GenOrderingPoly =
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
