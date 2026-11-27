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

package zio.test.laws

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.{Gen, TestResult, check}
import zio.{URIO, ZIO, Trace}

/**
 * `ZLaws2[CapsBoth, CapsLeft, CapsRight, R]` represents a set of laws about a
 * pair of types `A` and `B`: `CapsBoth[A, B]` is a capability relating the two
 * types (for example an equivalence between them), and `CapsLeft` and
 * `CapsRight` are the capabilities required of `A` and `B`. Laws are run by
 * providing a generator for each type, and can be combined using `+`.
 *
 * {{{
 * import zio.UIO
 * import zio.test._
 *
 * trait Equal[-A] {
 *   def equal(a1: A, a2: A): Boolean
 * }
 *
 * final case class Equivalence[A, B](to: A => B, from: B => A)
 *
 * val leftIdentity: Laws2.Law1Left[Equivalence, Equal, Equal] =
 *   new Laws2.Law1Left[Equivalence, Equal, Equal]("leftIdentity") {
 *     def apply[A: Equal, B: Equal](a1: A)(implicit eqv: Equivalence[A, B]): TestResult =
 *       assertTrue(implicitly[Equal[A]].equal(eqv.from(eqv.to(a1)), a1))
 *   }
 *
 * val rightIdentity: Laws2.Law1Right[Equivalence, Equal, Equal] =
 *   new Laws2.Law1Right[Equivalence, Equal, Equal]("rightIdentity") {
 *     def apply[A: Equal, B: Equal](b1: B)(implicit eqv: Equivalence[A, B]): TestResult =
 *       assertTrue(implicitly[Equal[B]].equal(eqv.to(eqv.from(b1)), b1))
 *   }
 *
 * val equivalenceLaws: Laws2[Equivalence, Equal, Equal] = leftIdentity + rightIdentity
 *
 * implicit val charsEqual: Equal[List[Char]] = new Equal[List[Char]] {
 *   def equal(a1: List[Char], a2: List[Char]): Boolean = a1 == a2
 * }
 * implicit val stringEqual: Equal[String] = new Equal[String] {
 *   def equal(a1: String, a2: String): Boolean = a1 == a2
 * }
 * implicit val charsString: Equivalence[List[Char], String] =
 *   Equivalence(_.mkString, _.toList)
 *
 * val result: UIO[TestResult] =
 *   equivalenceLaws.run(Gen.listOf(Gen.char), Gen.string)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
abstract class ZLaws2[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_], -R] { self =>

  /**
   * Test that pairs of values drawn from `left` and `right` satisfy the laws,
   * given the `CapsBoth[A, B]` instance that relates the two types.
   *
   * The constructors in `ZLaws2` implement it with `check`, which draws a value
   * from each generator for each sample (200 samples by default) and stops a
   * law at its first failing sample. The effect itself does not fail; a failed
   * law gives a failed `TestResult` labelled with the law's name.
   */
  def run[R1 <: R, A: CapsLeft, B: CapsRight](left: Gen[R1, A], right: Gen[R1, B])(implicit
    CapsBoth: CapsBoth[A, B],
    trace: Trace
  ): ZIO[R1, Nothing, TestResult]

  /**
   * Combine these laws with the specified laws to produce a set of laws that
   * require both sets of laws to be satisfied.
   *
   * `run` on the result runs these laws, then `that`, and joins the two results
   * with `&&`. The laws of `that` still run when these laws fail.
   */
  def +[CapsBoth1[x, y] <: CapsBoth[x, y], CapsLeft1[x] <: CapsLeft[x], CapsRight1[x] <: CapsRight[x], R1 <: R](
    that: ZLaws2[CapsBoth1, CapsLeft1, CapsRight1, R1]
  ): ZLaws2[CapsBoth1, CapsLeft1, CapsRight1, R1] =
    ZLaws2.Both(self, that)
}

/**
 * Constructors of single laws about a pair of types. Extend one of them and
 * define its `apply` method; the `label` given to the constructor names the law
 * in test results.
 */
object ZLaws2 {

  private final case class Both[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_], -R](
    left: ZLaws2[CapsBoth, CapsLeft, CapsRight, R],
    right: ZLaws2[CapsBoth, CapsLeft, CapsRight, R]
  ) extends ZLaws2[CapsBoth, CapsLeft, CapsRight, R] {
    final def run[R1 <: R, A: CapsLeft, B: CapsRight](a: Gen[R1, A], b: Gen[R1, B])(implicit
      CapsBoth: CapsBoth[A, B],
      trace: Trace
    ): ZIO[R1, Nothing, TestResult] =
      left.run(a, b).zipWith(right.run(a, b))(_ && _)
  }

  /**
   * Constructs a law from a pure function taking a single value of the left
   * type `A`.
   */
  abstract class Law1Left[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_]](label: String)
      extends ZLaws2[CapsBoth, CapsLeft, CapsRight, Any] { self =>

    /**
     * Checks the law for `a1`, using the `CapsBoth[A, B]` instance, and returns
     * a successful `TestResult` when the law holds.
     */
    def apply[A: CapsLeft, B: CapsRight](a1: A)(implicit CapsBoth: CapsBoth[A, B]): TestResult

    /**
     * Checks this law with `check` for values from `a` (200 samples by
     * default). It also draws a value from `b` for each sample and does not use
     * it. The result is labelled with the law's name.
     */
    final def run[R, A: CapsLeft, B: CapsRight](a: Gen[R, A], b: Gen[R, B])(implicit
      CapsBoth: CapsBoth[A, B],
      trace: Trace
    ): URIO[R, TestResult] =
      check(a, b)((a, _) => apply(a).label(label))
  }

  /**
   * Constructs a law from a pure function taking a single value of the right
   * type `B`.
   */
  abstract class Law1Right[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_]](label: String)
      extends ZLaws2[CapsBoth, CapsLeft, CapsRight, Any] { self =>

    /**
     * Checks the law for `b1`, using the `CapsBoth[A, B]` instance, and returns
     * a successful `TestResult` when the law holds.
     */
    def apply[A: CapsLeft, B: CapsRight](b1: B)(implicit CapsBoth: CapsBoth[A, B]): TestResult

    /**
     * Checks this law with `check` for values from `b` (200 samples by
     * default). It also draws a value from `a` for each sample and does not use
     * it. The result is labelled with the law's name.
     */
    final def run[R, A: CapsLeft, B: CapsRight](a: Gen[R, A], b: Gen[R, B])(implicit
      CapsBoth: CapsBoth[A, B],
      trace: Trace
    ): URIO[R, TestResult] =
      check(a, b)((_, b) => apply(b).label(label))
  }
}
