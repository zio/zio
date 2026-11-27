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
import zio.{URIO, ZIO}
import zio.Trace

/**
 * Laws for type constructors with two parameters. `ZLawsF2.Divariant[CapsF, CapsLeft, CapsRight, R]` describes a set of laws that a type `F[A, B]`, contravariant in `A` and covariant in `B` (such as `Function1`), with capabilities `CapsF` is expected to satisfy for types `A` with capabilities `CapsLeft` and `B` with capabilities `CapsRight`.
 *
 * @see [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
object ZLawsF2 {

  /**
   * `ZLawsF2` for Divariant type constructors `F[-_, +_]`, such as `Function1`. Laws are run with a `GenF2`, which builds a generator of `F[A, B]` values from a generator of `B` values, and can be combined using `+`.
   *
   * {{{
   * import zio.UIO
   * import zio.test._
   *
   * trait Applicable[F[-_, +_]] {
   *   def applyTo[A, B](fab: F[A, B], a: A): B
   * }
   *
   * trait Default[A] {
   *   def value: A
   * }
   *
   * val deterministic: LawsF2.Divariant.Law1[Applicable, Default, Default] =
   *   new LawsF2.Divariant.Law1[Applicable, Default, Default]("deterministic") {
   *     def apply[F[-_, +_]: Applicable, A: Default, B: Default](fab: F[A, B]): TestResult = {
   *       val ap = implicitly[Applicable[F]]
   *       val a  = implicitly[Default[A]].value
   *       assertTrue(ap.applyTo(fab, a) == ap.applyTo(fab, a))
   *     }
   *   }
   *
   * implicit val function1Applicable: Applicable[Function1] = new Applicable[Function1] {
   *   def applyTo[A, B](fab: A => B, a: A): B = fab(a)
   * }
   * implicit val stringDefault: Default[String] = new Default[String] {
   *   def value: String = "zio"
   * }
   * implicit val intDefault: Default[Int] = new Default[Int] {
   *   def value: Int = 0
   * }
   *
   * val result: UIO[TestResult] =
   *   deterministic.run[Any, Function1, String, Int](GenF2.function1, Gen.int)
   * }}}
   */
  abstract class Divariant[-CapsF[_[-_, +_]], -CapsLeft[_], -CapsRight[_], -R] { self =>

    /**
     * Test that values of type `F[A, B]`, for a type constructor `F[-_, +_]`, satisfy the laws, using `genF` to construct a generator of `F[A, B]` values from the generator `gen` of `B` values.
     *
     * `A` appears only in the required `CapsLeft[A]` instance, so callers usually pass the type arguments explicitly. The effect itself does not fail; a failed law gives a failed `TestResult`.
     */
    def run[R1 <: R, F[-_, +_]: CapsF, A: CapsLeft, B: CapsRight](
      genF: GenF2[R1, F],
      gen: Gen[R1, B]
    )(implicit trace: Trace): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that require both sets of laws to be satisfied.
     *
     * `run` on the result runs these laws, then `that`, and joins the two results with `&&`. The laws of `that` still run when these laws fail.
     */
    def +[CapsF1[x[-_, +_]] <: CapsF[x], CapsLeft1[x] <: CapsLeft[x], CapsRight1[x] <: CapsRight[x], R1 <: R](
      that: Divariant[CapsF1, CapsLeft1, CapsRight1, R1]
    ): Divariant[CapsF1, CapsLeft1, CapsRight1, R1] =
      Divariant.Both(self, that)
  }

  /**
   * Constructors of single `ZLawsF2.Divariant` laws. Extend one of them and define its `apply` method; the `label` given to the constructor names the law in test results.
   */
  object Divariant {

    private final case class Both[-CapsBothF[_[-_, +_]], -CapsLeft[_], -CapsRight[_], -R](
      left: Divariant[CapsBothF, CapsLeft, CapsRight, R],
      right: Divariant[CapsBothF, CapsLeft, CapsRight, R]
    ) extends Divariant[CapsBothF, CapsLeft, CapsRight, R] {

      override final def run[R1 <: R, F[-_, +_]: CapsBothF, A: CapsLeft, B: CapsRight](
        genF: GenF2[R1, F],
        gen: Gen[R1, B]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] = {
        val lhs: ZIO[R1, Nothing, TestResult] = left.run(genF, gen)
        val rhs: ZIO[R1, Nothing, TestResult] = right.run(genF, gen)
        lhs.zipWith(rhs)(_ && _)
      }
    }

    /**
     * Constructs a law from a pure function taking one parameterized value and two functions that can be composed.
     *
     * Its `run` method takes four arguments and is an overload: the two-argument `run` declared by `Divariant`, which `+` calls, is not defined by `ComposeLaw`, so a subclass has to define it as well.
     */
    abstract class ComposeLaw[-CapsBothF[_[-_, +_]], -Caps[_]](label: String)
        extends Divariant[CapsBothF, Caps, Caps, Any] {
      self =>
      /**
       * Checks the law for `fa` and the functions `f` and `g`, returning a successful `TestResult` when the law holds.
       */
      def apply[F[-_, +_]: CapsBothF, A: Caps, B: Caps, A1: Caps, A2: Caps](
        fa: F[A, B],
        f: A => A1,
        g: A1 => A2
      ): TestResult

      /**
       * Checks this law with `check`, drawing `fa` from `genF` applied to `genB`, `f` from `Gen.function(genA1)` and `g` from `Gen.function(genA2)` for each sample (200 samples by default). The result is labelled with the law's name.
       */
      final def run[R, F[-_, +_]: CapsBothF, A: Caps, B: Caps, A1: Caps, A2: Caps](
        genF: GenF2[R, F],
        genB: Gen[R, B],
        genA1: Gen[R, A1],
        genA2: Gen[R, A2]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(
          genF[R, A, B](genB),
          Gen.function[R, A, A1](genA1),
          Gen.function[R, A1, A2](genA2)
        )(apply(_, _, _).label(label))
    }

    /**
     * Constructs a law from a pure function taking a single parameter.
     */
    abstract class Law1[-CapsBothF[_[-_, +_]], -CapsLeft[_], -CapsRight[_]](label: String)
        extends Divariant[CapsBothF, CapsLeft, CapsRight, Any] { self =>

      /**
       * Checks the law for `fa`, returning a successful `TestResult` when the law holds.
       */
      def apply[F[-_, +_]: CapsBothF, A: CapsLeft, B: CapsRight](fa: F[A, B]): TestResult

      /**
       * Checks this law with `check` for values from `genF` applied to `gen` (200 samples by default). The result is labelled with the law's name.
       */
      final def run[R, F[-_, +_]: CapsBothF, A: CapsLeft, B: CapsRight](
        genF: GenF2[R, F],
        gen: Gen[R, B]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF[R, A, B](gen))(apply(_).label(label))
    }
  }
}
