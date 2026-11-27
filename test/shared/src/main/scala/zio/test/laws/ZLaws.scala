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
 * `ZLaws[Caps, R]` represents a set of laws that values with capabilities `Caps` are expected to satisfy. Laws can be run by providing a generator of values of a type `A` with the required capabilities to return a test result. Laws can be combined using `+` to produce a set of laws that require both sets of laws to be satisfied.
 *
 * A single law is written by extending one of the constructors in the companion object (`ZLaws.Law1`, `ZLaws.Law2`, `ZLaws.Law3` and their effectual `ZIO` variants), or their aliases in `Laws`. `R` is the environment the laws need, `Any` for pure laws.
 *
 * {{{
 * import zio.UIO
 * import zio.test._
 *
 * trait Equal[-A] {
 *   def equal(a1: A, a2: A): Boolean
 * }
 *
 * val reflexive: Laws.Law1[Equal] =
 *   new Laws.Law1[Equal]("reflexive") {
 *     def apply[A: Equal](a1: A): TestResult =
 *       assertTrue(implicitly[Equal[A]].equal(a1, a1))
 *   }
 *
 * val symmetric: Laws.Law2[Equal] =
 *   new Laws.Law2[Equal]("symmetric") {
 *     def apply[A: Equal](a1: A, a2: A): TestResult = {
 *       val eq = implicitly[Equal[A]]
 *       assertTrue(eq.equal(a1, a2) == eq.equal(a2, a1))
 *     }
 *   }
 *
 * val equalLaws: ZLaws[Equal, Any] = reflexive + symmetric
 *
 * implicit val intEqual: Equal[Int] = new Equal[Int] {
 *   def equal(a1: Int, a2: Int): Boolean = a1 == a2
 * }
 *
 * val result: UIO[TestResult] = equalLaws.run(Gen.int)
 * }}}
 *
 * @see [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
abstract class ZLaws[-Caps[_], -R] { self =>

  /**
   * Test that values of type `A` satisfy the laws using the specified generator.
   *
   * The constructors in `ZLaws` implement it with `check`: each law draws its own values from `gen`, so two combined laws do not see the same values. By default each law checks 200 samples (the `samples` setting of `TestConfig`), and a law with two or three parameters draws each of them separately. A law stops at its first failing sample, which is shrunk and reported in the returned failed `TestResult`, labelled with the law's name. The effect itself does not fail.
   */
  def run[R1 <: R, A: Caps](gen: Gen[R1, A])(implicit
    trace: Trace
  ): ZIO[R1, Nothing, TestResult]

  /**
   * Combine these laws with the specified laws to produce a set of laws that require both sets of laws to be satisfied.
   *
   * `run` on the result runs these laws, then `that`, and joins the two results with `&&`. The laws of `that` still run when these laws fail. The capability of the result is `Caps1`, a subtype of both capabilities: combining laws for two unrelated capabilities needs one instance that has both.
   */
  def +[Caps1[x] <: Caps[x], R1 <: R](that: ZLaws[Caps1, R1]): ZLaws[Caps1, R1] =
    ZLaws.Both(self, that)
}

/**
 * Constructors of single laws. Extend one of them and define its `apply` method, which checks the law for the given values and returns a `TestResult`. The `label` given to the constructor names the law in test results.
 */
object ZLaws {

  private final case class Both[-Caps[_], -R](left: ZLaws[Caps, R], right: ZLaws[Caps, R]) extends ZLaws[Caps, R] {
    final def run[R1 <: R, A: Caps](gen: Gen[R1, A])(implicit
      trace: Trace
    ): ZIO[R1, Nothing, TestResult] =
      left.run(gen).zipWith(right.run(gen))(_ && _)
  }

  /**
   * Constructs a law from a pure function taking a single parameter.
   */
  abstract class Law1[-Caps[_]](label: String) extends ZLaws[Caps, Any] { self =>

    /**
     * Checks the law for `a1`, returning a successful `TestResult` when the law holds.
     */
    def apply[A: Caps](a1: A): TestResult

    /**
     * Checks this law with `check` for values from `gen` (200 samples by default). The result is labelled with the law's name.
     */
    final def run[R, A: Caps](gen: Gen[R, A])(implicit trace: Trace): URIO[R, TestResult] =
      check(gen)(apply(_).??(label))
  }

  /**
   * Constructs a law from an effectual function taking a single parameter.
   */
  abstract class Law1ZIO[-Caps[_], -R](label: String) extends ZLaws[Caps, R] { self =>

    /**
     * Checks the law for `a1` with an effect that needs an `R`, returning a successful `TestResult` when the law holds.
     */
    def apply[A: Caps](a1: A): URIO[R, TestResult]

    /**
     * Checks this law with `check` for values from `gen` (200 samples by default). It runs the effect returned by `apply` once per sample. The result is labelled with the law's name.
     */
    final def run[R1 <: R, A: Caps](gen: Gen[R1, A])(implicit
      trace: Trace
    ): ZIO[R1, Nothing, TestResult] =
      check(gen)(apply(_).map(_.??(label)))
  }

  /**
   * Constructs a law from a pure function taking two parameters.
   */
  abstract class Law2[-Caps[_]](label: String) extends ZLaws[Caps, Any] { self =>

    /**
     * Checks the law for `a1` and `a2`, returning a successful `TestResult` when the law holds.
     */
    def apply[A: Caps](a1: A, a2: A): TestResult

    /**
     * Checks this law with `check`, drawing `a1` and `a2` separately from `gen` for each sample (200 samples by default). The result is labelled with the law's name.
     */
    final def run[R, A: Caps](gen: Gen[R, A])(implicit trace: Trace): URIO[R, TestResult] =
      check(gen, gen)(apply(_, _).label(label))
  }

  /**
   * Constructs a law from an effectual function taking two parameters.
   */
  abstract class Law2ZIO[-Caps[_], -R](label: String) extends ZLaws[Caps, R] { self =>

    /**
     * Checks the law for `a1` and `a2` with an effect that needs an `R`, returning a successful `TestResult` when the law holds.
     */
    def apply[A: Caps](a1: A, a2: A): URIO[R, TestResult]

    /**
     * Checks this law with `check`, drawing `a1` and `a2` separately from `gen` for each sample (200 samples by default). It runs the effect returned by `apply` once per sample. The result is labelled with the law's name.
     */
    final def run[R1 <: R, A: Caps](gen: Gen[R1, A])(implicit
      trace: Trace
    ): ZIO[R1, Nothing, TestResult] =
      check(gen, gen)(apply(_, _).map(_.label(label)))
  }

  /**
   * Constructs a law from a pure function taking three parameters.
   */
  abstract class Law3[-Caps[_]](label: String) extends ZLaws[Caps, Any] { self =>

    /**
     * Checks the law for `a1`, `a2` and `a3`, returning a successful `TestResult` when the law holds.
     */
    def apply[A: Caps](a1: A, a2: A, a3: A): TestResult

    /**
     * Checks this law with `check`, drawing `a1`, `a2` and `a3` separately from `gen` for each sample (200 samples by default). The result is labelled with the law's name.
     */
    final def run[R, A: Caps](gen: Gen[R, A])(implicit trace: Trace): URIO[R, TestResult] =
      check(gen, gen, gen)(apply(_, _, _).label(label))
  }

  /**
   * Constructs a law from an effectual function taking three parameters.
   */
  abstract class Law3ZIO[-Caps[_], -R](label: String) extends ZLaws[Caps, R] { self =>

    /**
     * Checks the law for `a1`, `a2` and `a3` with an effect that needs an `R`, returning a successful `TestResult` when the law holds.
     */
    def apply[A: Caps](a1: A, a2: A, a3: A): URIO[R, TestResult]

    /**
     * Checks this law with `check`, drawing `a1`, `a2` and `a3` separately from `gen` for each sample (200 samples by default). It runs the effect returned by `apply` once per sample. The result is labelled with the law's name.
     */
    final def run[R1 <: R, A: Caps](gen: Gen[R1, A])(implicit
      trace: Trace
    ): ZIO[R1, Nothing, TestResult] =
      check(gen, gen, gen)(apply(_, _, _).map(_.label(label)))
  }
}
