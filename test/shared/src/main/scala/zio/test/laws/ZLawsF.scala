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
 * Laws for parameterized types. `ZLawsF.Covariant[CapsF, Caps, R]`,
 * `ZLawsF.Contravariant[CapsF, Caps, R]` and `ZLawsF.Invariant[CapsF, Caps, R]`
 * describe a set of laws that a parameterized type `F[A]` with capabilities
 * `CapsF` is expected to satisfy with respect to all types `A` that have
 * capabilities `Caps`. Laws can be run by providing a `GenF` that is capable of
 * generating `F[A]` values given a generator of `A` values and a generator of
 * values of some type `A`. Laws can be combined using `+` to produce a set of
 * laws that require both sets of laws to be satisfied.
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
object ZLawsF {

  /**
   * `ZLawsF` for covariant type constructors `F[+_]`, such as `Option` or
   * `List`. Single laws are written by extending a constructor in the companion
   * object.
   *
   * {{{
   * import zio.UIO
   * import zio.test._
   *
   * trait Equal[-A] {
   *   def equal(a1: A, a2: A): Boolean
   * }
   *
   * trait Mappable[F[+_]] {
   *   def map[A, B](fa: F[A])(f: A => B): F[B]
   * }
   *
   * val identityLaw: LawsF.Covariant[Mappable, Equal] =
   *   new LawsF.Covariant.Law1[Mappable, Equal]("identityLaw") {
   *     def apply[F[+_]: Mappable, A: Equal](fa: F[A]): TestResult = {
   *       val mapped = implicitly[Mappable[F]].map(fa)(identity)
   *       assertTrue(mapped == fa)
   *     }
   *   }
   *
   * val compositionLaw: LawsF.Covariant[Mappable, Equal] =
   *   new LawsF.Covariant.ComposeLaw[Mappable, Equal]("compositionLaw") {
   *     def apply[F[+_]: Mappable, A: Equal, B: Equal, C: Equal](fa: F[A], f: A => B, g: B => C): TestResult = {
   *       val m     = implicitly[Mappable[F]]
   *       val left  = m.map(m.map(fa)(f))(g)
   *       val right = m.map(fa)(f.andThen(g))
   *       assertTrue(left == right)
   *     }
   *   }
   *
   * val mappableLaws: LawsF.Covariant[Mappable, Equal] = identityLaw + compositionLaw
   *
   * implicit val optionMappable: Mappable[Option] = new Mappable[Option] {
   *   def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa.map(f)
   * }
   * implicit val intEqual: Equal[Int] = new Equal[Int] {
   *   def equal(a1: Int, a2: Int): Boolean = a1 == a2
   * }
   *
   * val result: UIO[TestResult] = mappableLaws.run(GenF.option, Gen.int)
   * }}}
   */
  abstract class Covariant[-CapsF[_[+_]], -Caps[_], -R] { self =>

    /**
     * Test that values of type `F[A]`, for a type constructor `F[+_]`, satisfy
     * the laws, using `genF` to construct a generator of `F[A]` values from the
     * generator `gen` of `A` values.
     *
     * The constructors in the companion object implement it with `check`: each
     * law draws its own samples from the generator built by `genF` (200 by
     * default) and stops at its first failing sample. The effect itself does
     * not fail; a failed law gives a failed `TestResult` labelled with the
     * law's name.
     */
    def run[R1 <: R, F[+_]: CapsF, A: Caps](
      genF: GenF[R1, F],
      gen: Gen[R1, A]
    )(implicit trace: Trace): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that
     * require both sets of laws to be satisfied.
     *
     * `run` on the result runs these laws, then `that`, and joins the two
     * results with `&&`. The laws of `that` still run when these laws fail.
     */
    def +[CapsF1[x[+_]] <: CapsF[x], Caps1[x] <: Caps[x], R1 <: R](
      that: Covariant[CapsF1, Caps1, R1]
    ): Covariant[CapsF1, Caps1, R1] =
      Covariant.Both(self, that)
  }

  /**
   * Constructors of single `ZLawsF.Covariant` laws. Extend one of them and
   * define its `apply` method; the `label` given to the constructor names the
   * law in test results.
   */
  object Covariant {

    private final case class Both[-CapsF[_[+_]], -Caps[_], -R](
      left: Covariant[CapsF, Caps, R],
      right: Covariant[CapsF, Caps, R]
    ) extends Covariant[CapsF, Caps, R] {
      final def run[R1 <: R, F[+_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        left.run(genF, gen).zipWith(right.run(genF, gen))(_ && _)
    }

    /**
     * Constructs a law from a pure function taking one parameterized value and
     * two functions that can be composed.
     */
    abstract class ComposeLaw[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa` and the functions `f` and `g`, returning a
       * successful `TestResult` when the law holds.
       */
      def apply[F[+_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], f: A => B, g: B => C): TestResult

      /**
       * Checks this law with `check`, drawing `fa` from `genF(gen)` and `f` and
       * `g` from `Gen.function(gen)` for each sample (200 samples by default),
       * so both functions return values drawn from `gen`. The result is
       * labelled with the law's name.
       */
      final def run[R, F[+_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen), Gen.function(gen), Gen.function(gen))(apply(_, _, _).label(label))
    }

    /**
     * Constructs a law from a parameterized value wrapped in two additional
     * layers that can be flattened.
     */
    abstract class FlattenLaw[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fffa`, a value nested three levels deep in `F`,
       * returning a successful `TestResult` when the law holds.
       */
      def apply[F[+_]: CapsF, A: Caps](fffa: F[F[F[A]]]): TestResult

      /**
       * Checks this law with `check` for values from `genF(genF(genF(gen)))`
       * (200 samples by default). The result is labelled with the law's name.
       */
      final def run[R, F[+_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(genF(genF(gen))))(apply(_).label(label))
    }

    /**
     * Constructs a law from a pure function taking a single parameter.
     */
    abstract class Law1[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa`, returning a successful `TestResult` when the
       * law holds.
       */
      def apply[F[+_]: CapsF, A: Caps](fa: F[A]): TestResult

      /**
       * Checks this law with `check` for values from `genF(gen)` (200 samples
       * by default). The result is labelled with the law's name.
       */
      final def run[R, F[+_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen))(apply(_).label(label))
    }

    /**
     * Constructs a law from an effectual function taking a single parameter.
     */
    abstract class Law1ZIO[-CapsF[_[+_]], -Caps[_], -R](label: String) extends Covariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa` with an effect that needs an `R`, returning a
       * successful `TestResult` when the law holds.
       */
      def apply[F[+_]: CapsF, A: Caps](fa: F[A]): URIO[R, TestResult]

      /**
       * Checks this law with `check` for values from `genF(gen)` (200 samples
       * by default). It runs the effect returned by `apply` once per sample,
       * and again for each smaller value tried while shrinking a failing
       * sample. The result is labelled with the law's name.
       */
      final def run[R1 <: R, F[+_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen))(apply(_).map(_.label(label)))
    }

    /**
     * Constructs a law from a pure function taking two parameters.
     */
    abstract class Law2[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa` and `fb`, returning a successful `TestResult`
       * when the law holds.
       */
      def apply[F[+_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): TestResult

      /**
       * Checks this law with `check`, drawing `fa` and `fb` separately from
       * `genF(gen)` for each sample (200 samples by default). The result is
       * labelled with the law's name.
       */
      final def run[R, F[+_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen), genF(gen))(apply(_, _).label(label))
    }

    /**
     * Constructs a law from an effectual function taking two parameters.
     */
    abstract class Law2ZIO[-CapsF[_[+_]], -Caps[_], -R](label: String) extends Covariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa` and `fb` with an effect that needs an `R`,
       * returning a successful `TestResult` when the law holds.
       */
      def apply[F[+_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): URIO[R, TestResult]

      /**
       * Checks this law with `check`, drawing `fa` and `fb` separately from
       * `genF(gen)` for each sample (200 samples by default). It runs the
       * effect returned by `apply` once per sample, and again for each smaller
       * value tried while shrinking a failing sample. The result is labelled
       * with the law's name.
       */
      final def run[R1 <: R, F[+_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen), genF(gen))(apply(_, _).map(_.label(label)))
    }

    /**
     * Constructs a law from a pure function taking three parameters.
     */
    abstract class Law3[-CapsF[_[+_]], -Caps[_]](label: String) extends Covariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa`, `fb` and `fc`, returning a successful
       * `TestResult` when the law holds.
       */
      def apply[F[+_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): TestResult

      /**
       * Checks this law with `check`, drawing `fa`, `fb` and `fc` separately
       * from `genF(gen)` for each sample (200 samples by default). The result
       * is labelled with the law's name.
       */
      final def run[R, F[+_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).label(label))
    }

    /**
     * Constructs a law from an effectual function taking three parameters.
     */
    abstract class Law3ZIO[-CapsF[_[+_]], -Caps[_], -R](label: String) extends Covariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa`, `fb` and `fc` with an effect that needs an
       * `R`, returning a successful `TestResult` when the law holds.
       */
      def apply[F[+_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): URIO[R, TestResult]

      /**
       * Checks this law with `check`, drawing `fa`, `fb` and `fc` separately
       * from `genF(gen)` for each sample (200 samples by default). It runs the
       * effect returned by `apply` once per sample, and again for each smaller
       * value tried while shrinking a failing sample. The result is labelled
       * with the law's name.
       */
      final def run[R1 <: R, F[+_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).map(_.label(label)))
    }
  }

  /**
   * `ZLawsF` for contravariant type constructors `F[-_]`, such as a type of
   * functions from `A` to a fixed result. Single laws are written by extending
   * a constructor in the companion object.
   */
  abstract class Contravariant[-CapsF[_[-_]], -Caps[_], -R] { self =>

    /**
     * Test that values of type `F[A]`, for a type constructor `F[-_]`, satisfy
     * the laws, using `genF` to construct a generator of `F[A]` values from the
     * generator `gen` of `A` values.
     *
     * The constructors in the companion object implement it with `check`: each
     * law draws its own samples from the generator built by `genF` (200 by
     * default) and stops at its first failing sample. The effect itself does
     * not fail; a failed law gives a failed `TestResult` labelled with the
     * law's name.
     */
    def run[R1 <: R, F[-_]: CapsF, A: Caps](
      genF: GenF[R1, F],
      gen: Gen[R1, A]
    )(implicit trace: Trace): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that
     * require both sets of laws to be satisfied.
     *
     * `run` on the result runs these laws, then `that`, and joins the two
     * results with `&&`. The laws of `that` still run when these laws fail.
     */
    def +[CapsF1[x[-_]] <: CapsF[x], Caps1[x] <: Caps[x], R1 <: R](
      that: Contravariant[CapsF1, Caps1, R1]
    ): Contravariant[CapsF1, Caps1, R1] =
      Contravariant.Both(self, that)
  }

  /**
   * Constructors of single `ZLawsF.Contravariant` laws. Extend one of them and
   * define its `apply` method; the `label` given to the constructor names the
   * law in test results.
   */
  object Contravariant {

    private final case class Both[-CapsF[_[-_]], -Caps[_], -R](
      left: Contravariant[CapsF, Caps, R],
      right: Contravariant[CapsF, Caps, R]
    ) extends Contravariant[CapsF, Caps, R] {
      final def run[R1 <: R, F[-_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        left.run(genF, gen).zipWith(right.run(genF, gen))(_ && _)
    }

    /**
     * Constructs a law from a pure function taking one parameterized value and
     * two functions that can be composed.
     */
    abstract class ComposeLaw[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any] {
      self =>

      /**
       * Checks the law for `fa` and the functions `f` and `g`, returning a
       * successful `TestResult` when the law holds.
       */
      def apply[F[-_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], f: B => A, g: C => B): TestResult

      /**
       * Checks this law with `check`, drawing `fa` from `genF(gen)` and `f` and
       * `g` as functions from `A` to `A` built with `Gen.function(gen)` for
       * each sample (200 samples by default). The result is labelled with the
       * law's name.
       */
      final def run[R, F[-_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen), Gen.function[R, A, A](gen), Gen.function[R, A, A](gen))(apply(_, _, _).label(label))
    }

    /**
     * Constructs a law from a pure function taking a single parameter.
     */
    abstract class Law1[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa`, returning a successful `TestResult` when the
       * law holds.
       */
      def apply[F[-_]: CapsF, A: Caps](fa: F[A]): TestResult

      /**
       * Checks this law with `check` for values from `genF(gen)` (200 samples
       * by default). The result is labelled with the law's name.
       */
      final def run[R, F[-_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen))(apply(_).label(label))
    }

    /**
     * Constructs a law from an effectual function taking a single parameter.
     */
    abstract class Law1ZIO[-CapsF[_[-_]], -Caps[_], -R](label: String) extends Contravariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa` with an effect that needs an `R`, returning a
       * successful `TestResult` when the law holds.
       */
      def apply[F[-_]: CapsF, A: Caps](fa: F[A]): URIO[R, TestResult]

      /**
       * Checks this law with `check` for values from `genF(gen)` (200 samples
       * by default). It runs the effect returned by `apply` once per sample,
       * and again for each smaller value tried while shrinking a failing
       * sample. The result is labelled with the law's name.
       */
      final def run[R1 <: R, F[-_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen))(apply(_).map(_.label(label)))
    }

    /**
     * Constructs a law from a pure function taking two parameters.
     */
    abstract class Law2[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa` and `fb`, returning a successful `TestResult`
       * when the law holds.
       */
      def apply[F[-_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): TestResult

      /**
       * Checks this law with `check`, drawing `fa` and `fb` separately from
       * `genF(gen)` for each sample (200 samples by default). The result is
       * labelled with the law's name.
       */
      final def run[R, F[-_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen), genF(gen))(apply(_, _).label(label))
    }

    /**
     * Constructs a law from an effectual function taking two parameters.
     */
    abstract class Law2ZIO[-CapsF[_[-_]], -Caps[_], -R](label: String) extends Contravariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa` and `fb` with an effect that needs an `R`,
       * returning a successful `TestResult` when the law holds.
       */
      def apply[F[-_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): URIO[R, TestResult]

      /**
       * Checks this law with `check`, drawing `fa` and `fb` separately from
       * `genF(gen)` for each sample (200 samples by default). It runs the
       * effect returned by `apply` once per sample, and again for each smaller
       * value tried while shrinking a failing sample. The result is labelled
       * with the law's name.
       */
      final def run[R1 <: R, F[-_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen), genF(gen))(apply(_, _).map(_.label(label)))
    }

    /**
     * Constructs a law from a pure function taking three parameters.
     */
    abstract class Law3[-CapsF[_[-_]], -Caps[_]](label: String) extends Contravariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa`, `fb` and `fc`, returning a successful
       * `TestResult` when the law holds.
       */
      def apply[F[-_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): TestResult

      /**
       * Checks this law with `check`, drawing `fa`, `fb` and `fc` separately
       * from `genF(gen)` for each sample (200 samples by default). The result
       * is labelled with the law's name.
       */
      final def run[R, F[-_]: CapsF, A: Caps](
        genF: GenF[R, F],
        gen: Gen[R, A]
      )(implicit trace: Trace): URIO[R, TestResult] =
        check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).label(label))
    }

    /**
     * Constructs a law from an effectual function taking three parameters.
     */
    abstract class Law3ZIO[-CapsF[_[-_]], -Caps[_], -R](label: String) extends Contravariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa`, `fb` and `fc` with an effect that needs an
       * `R`, returning a successful `TestResult` when the law holds.
       */
      def apply[F[-_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): URIO[R, TestResult]

      /**
       * Checks this law with `check`, drawing `fa`, `fb` and `fc` separately
       * from `genF(gen)` for each sample (200 samples by default). It runs the
       * effect returned by `apply` once per sample, and again for each smaller
       * value tried while shrinking a failing sample. The result is labelled
       * with the law's name.
       */
      final def run[R1 <: R, F[-_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).map(_.label(label)))
    }
  }

  /**
   * `ZLawsF` for invariant type constructors `F[_]`. Single laws are written by
   * extending a constructor in the companion object.
   */
  abstract class Invariant[-CapsF[_[_]], -Caps[_], -R] { self =>

    /**
     * Test that values of type `F[A]`, for a type constructor `F[_]`, satisfy
     * the laws, using `genF` to construct a generator of `F[A]` values from the
     * generator `gen` of `A` values.
     *
     * The constructors in the companion object implement it with `check`: each
     * law draws its own samples from the generator built by `genF` (200 by
     * default) and stops at its first failing sample. The effect itself does
     * not fail; a failed law gives a failed `TestResult` labelled with the
     * law's name.
     */
    def run[R1 <: R, F[_]: CapsF, A: Caps](
      genF: GenF[R1, F],
      gen: Gen[R1, A]
    )(implicit trace: Trace): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that
     * require both sets of laws to be satisfied.
     *
     * `run` on the result runs these laws, then `that`, and joins the two
     * results with `&&`. The laws of `that` still run when these laws fail.
     */
    def +[CapsF1[x[_]] <: CapsF[x], Caps1[x] <: Caps[x], R1 <: R](
      that: Invariant[CapsF1, Caps1, R1]
    ): Invariant[CapsF1, Caps1, R1] =
      Invariant.Both(self, that)
  }

  /**
   * Constructors of single `ZLawsF.Invariant` laws. Extend one of them and
   * define its `apply` method; the `label` given to the constructor names the
   * law in test results.
   */
  object Invariant {

    private final case class Both[-CapsF[_[_]], -Caps[_], -R](
      left: Invariant[CapsF, Caps, R],
      right: Invariant[CapsF, Caps, R]
    ) extends Invariant[CapsF, Caps, R] {
      final def run[R1 <: R, F[_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        left.run(genF, gen).zipWith(right.run(genF, gen))(_ && _)
    }

    /**
     * Constructs a law from a pure function taking a single parameter.
     */
    abstract class Law1[-CapsF[_[_]], -Caps[_]](label: String) extends Invariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa`, returning a successful `TestResult` when the
       * law holds.
       */
      def apply[F[_]: CapsF, A: Caps](fa: F[A]): TestResult

      /**
       * Checks this law with `check` for values from `genF(gen)` (200 samples
       * by default). The result is labelled with the law's name.
       */
      final def run[R, F[_]: CapsF, A: Caps](genF: GenF[R, F], gen: Gen[R, A])(implicit
        trace: Trace
      ): URIO[R, TestResult] =
        check(genF(gen))(apply(_).label(label))
    }

    /**
     * Constructs a law from an effectual function taking a single parameter.
     */
    abstract class Law1ZIO[-CapsF[_[_]], -Caps[_], -R](label: String) extends Invariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa` with an effect that needs an `R`, returning a
       * successful `TestResult` when the law holds.
       */
      def apply[F[_]: CapsF, A: Caps](fa: F[A]): URIO[R, TestResult]

      /**
       * Checks this law with `check` for values from `genF(gen)` (200 samples
       * by default). It runs the effect returned by `apply` once per sample,
       * and again for each smaller value tried while shrinking a failing
       * sample. The result is labelled with the law's name.
       */
      final def run[R1 <: R, F[_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen))(apply(_).map(_.label(label)))
    }

    /**
     * Constructs a law from a pure function taking two parameters.
     */
    abstract class Law2[-CapsF[_[_]], -Caps[_]](label: String) extends Invariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa` and `fb`, returning a successful `TestResult`
       * when the law holds.
       */
      def apply[F[_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): TestResult

      /**
       * Checks this law with `check`, drawing `fa` and `fb` separately from
       * `genF(gen)` for each sample (200 samples by default). The result is
       * labelled with the law's name.
       */
      final def run[R, F[_]: CapsF, A: Caps](genF: GenF[R, F], gen: Gen[R, A])(implicit
        trace: Trace
      ): URIO[R, TestResult] =
        check(genF(gen), genF(gen))(apply(_, _).label(label))
    }

    /**
     * Constructs a law from an effectual function taking two parameters.
     */
    abstract class Law2ZIO[-CapsF[_[_]], -Caps[_], -R](label: String) extends Invariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa` and `fb` with an effect that needs an `R`,
       * returning a successful `TestResult` when the law holds.
       */
      def apply[F[_]: CapsF, A: Caps, B: Caps](fa: F[A], fb: F[B]): URIO[R, TestResult]

      /**
       * Checks this law with `check`, drawing `fa` and `fb` separately from
       * `genF(gen)` for each sample (200 samples by default). It runs the
       * effect returned by `apply` once per sample, and again for each smaller
       * value tried while shrinking a failing sample. The result is labelled
       * with the law's name.
       */
      final def run[R1 <: R, F[_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen), genF(gen))(apply(_, _).map(_.label(label)))
    }

    /**
     * Constructs a law from a pure function taking three parameters.
     */
    abstract class Law3[-CapsF[_[_]], -Caps[_]](label: String) extends Invariant[CapsF, Caps, Any] { self =>

      /**
       * Checks the law for `fa`, `fb` and `fc`, returning a successful
       * `TestResult` when the law holds.
       */
      def apply[F[_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): TestResult

      /**
       * Checks this law with `check`, drawing `fa`, `fb` and `fc` separately
       * from `genF(gen)` for each sample (200 samples by default). The result
       * is labelled with the law's name.
       */
      final def run[R, F[_]: CapsF, A: Caps](genF: GenF[R, F], gen: Gen[R, A])(implicit
        trace: Trace
      ): URIO[R, TestResult] =
        check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).label(label))
    }

    /**
     * Constructs a law from an effectual function taking three parameters.
     */
    abstract class Law3ZIO[-CapsF[_[_]], -Caps[_], -R](label: String) extends Invariant[CapsF, Caps, R] { self =>

      /**
       * Checks the law for `fa`, `fb` and `fc` with an effect that needs an
       * `R`, returning a successful `TestResult` when the law holds.
       */
      def apply[F[_]: CapsF, A: Caps, B: Caps, C: Caps](fa: F[A], fb: F[B], fc: F[C]): URIO[R, TestResult]

      /**
       * Checks this law with `check`, drawing `fa`, `fb` and `fc` separately
       * from `genF(gen)` for each sample (200 samples by default). It runs the
       * effect returned by `apply` once per sample, and again for each smaller
       * value tried while shrinking a failing sample. The result is labelled
       * with the law's name.
       */
      final def run[R1 <: R, F[_]: CapsF, A: Caps](
        genF: GenF[R1, F],
        gen: Gen[R1, A]
      )(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
        check(genF(gen), genF(gen), genF(gen))(apply(_, _, _).map(_.label(label)))
    }
  }
}
