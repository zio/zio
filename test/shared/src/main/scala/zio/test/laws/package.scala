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

package zio.test

import zio.ZIO
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.Trace

/**
 * The `laws` package provides functionality for describing laws as values. The
 * fundamental abstraction is a set of `ZLaws[Caps, R]`. These laws model the
 * laws that instances having a capability of type `Caps` are expected to
 * satisfy. A capability `Caps[_]` is an abstraction describing some
 * functionality that is common across different data types and obeys certain
 * laws. For example, we can model the capability of two values of a type being
 * compared for equality as follows:
 *
 * {{{
 * trait Equal[-A] {
 *   def equal(a1: A, a2: A): Boolean
 * }
 * }}}
 *
 * Definitions of equality are expected to obey certain laws:
 *   1. Reflexivity - `a1 === a1`
 *   1. Symmetry - `a1 === a2 ==> a2 === a1`
 *   1. Transitivity - `(a1 === a2) && (a2 === a3) ==> (a1 === a3)`
 *
 * These laws define what the capabilities mean and ensure that it is safe to
 * abstract across different instances with the same capability.
 *
 * Using ZIO Test, we can represent these laws as values. To do so, we define
 * each law by extending one of the `Laws` constructors (`Laws.Law1`,
 * `Laws.Law2` and `Laws.Law3` take one, two and three values), and we combine
 * laws using the `+` operator:
 *
 * {{{
 * import zio.test._
 * import zio.test.laws._
 *
 * trait Equal[-A] {
 *   def equal(a1: A, a2: A): Boolean
 * }
 *
 * val reflexivityLaw: Laws.Law1[Equal] =
 *   new Laws.Law1[Equal]("reflexivityLaw") {
 *     def apply[A: Equal](a1: A): TestResult =
 *       assertTrue(implicitly[Equal[A]].equal(a1, a1))
 *   }
 *
 * val symmetryLaw: Laws.Law2[Equal] =
 *   new Laws.Law2[Equal]("symmetryLaw") {
 *     def apply[A: Equal](a1: A, a2: A): TestResult = {
 *       val eq = implicitly[Equal[A]]
 *       assertTrue(eq.equal(a1, a2) == eq.equal(a2, a1))
 *     }
 *   }
 *
 * val transitivityLaw: Laws.Law3[Equal] =
 *   new Laws.Law3[Equal]("transitivityLaw") {
 *     def apply[A: Equal](a1: A, a2: A, a3: A): TestResult = {
 *       val eq = implicitly[Equal[A]]
 *       assertTrue(!(eq.equal(a1, a2) && eq.equal(a2, a3)) || eq.equal(a1, a3))
 *     }
 *   }
 *
 * val equalLaws: Laws[Equal] = reflexivityLaw + symmetryLaw + transitivityLaw
 * }}}
 *
 * Laws have a `run` method that takes a generator of values of type `A` and
 * checks that those values satisfy the laws. In addition, objects can extend
 * `ZLawful` to provide an even more convenient syntax for users to check that
 * instances satisfy certain laws.
 *
 * {{{
 * import zio.UIO
 * import zio.test._
 * import zio.test.laws._
 *
 * trait Equal[-A] {
 *   def equal(a1: A, a2: A): Boolean
 * }
 *
 * trait Hash[-A] extends Equal[A] {
 *   def hash(a: A): Int
 * }
 *
 * object Equal extends Lawful[Equal] {
 *   val laws: Laws[Equal] =
 *     new Laws.Law1[Equal]("reflexivityLaw") {
 *       def apply[A: Equal](a1: A): TestResult =
 *         assertTrue(implicitly[Equal[A]].equal(a1, a1))
 *     }
 * }
 *
 * object Hash extends Lawful[Hash] {
 *   val laws: Laws[Hash] =
 *     new Laws.Law2[Hash]("consistencyLaw") {
 *       def apply[A: Hash](a1: A, a2: A): TestResult = {
 *         val hash = implicitly[Hash[A]]
 *         assertTrue(!hash.equal(a1, a2) || hash.hash(a1) == hash.hash(a2))
 *       }
 *     }
 * }
 *
 * implicit val intHash: Hash[Int] = new Hash[Int] {
 *   def equal(a1: Int, a2: Int): Boolean = a1 == a2
 *   def hash(a: Int): Int                = a.hashCode
 * }
 *
 * val result: UIO[TestResult] = checkAllLaws(Equal + Hash)(Gen.int)
 * }}}
 *
 * Capabilities compose because laws are contravariant in the capability type.
 * Combining laws describing different capabilities gives a set of laws that
 * require an instance having all of the capabilities to satisfy each of the
 * laws, so `checkAllLaws` then needs one instance that has every capability,
 * such as `intHash`.
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
package object laws {

  /**
   * A `ZLawful` whose laws need no environment (`R = Any`).
   */
  type Lawful[-Caps[_]] = ZLawful[Caps, Any]

  /**
   * A `ZLawful2` whose laws need no environment (`R = Any`).
   */
  type Lawful2[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_]] = ZLawful2[CapsBoth, CapsLeft, CapsRight, Any]

  /**
   * A set of `ZLaws` that needs no environment (`R = Any`).
   */
  type Laws[-Caps[_]] = ZLaws[Caps, Any]

  /**
   * A set of `ZLaws2` that needs no environment (`R = Any`).
   */
  type Laws2[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_]] = ZLaws2[CapsBoth, CapsLeft, CapsRight, Any]

  /**
   * Aliases of the `ZLawfulF` lawful instances whose laws need no environment
   * (`R = Any`).
   */
  object LawfulF {

    /**
     * `ZLawfulF.Covariant` with no environment (`R = Any`).
     */
    type Covariant[-CapsF[_[+_]], -Caps[_]] = ZLawfulF.Covariant[CapsF, Caps, Any]

    /**
     * `ZLawfulF.Contravariant` with no environment (`R = Any`).
     */
    type Contravariant[-CapsF[_[-_]], -Caps[_]] = ZLawfulF.Contravariant[CapsF, Caps, Any]

    /**
     * `ZLawfulF.Invariant` with no environment (`R = Any`).
     */
    type Invariant[-CapsF[_[_]], -Caps[_]] = ZLawfulF.Invariant[CapsF, Caps, Any]
  }

  /**
   * Aliases of the `ZLawfulF2` lawful instances whose laws need no environment
   * (`R = Any`).
   */
  object LawfulF2 {

    /**
     * `ZLawfulF2.Divariant` with no environment (`R = Any`).
     */
    type Divariant[-CapsBoth[_[-_, +_]], -CapsLeft[_], -CapsRight[_]] =
      ZLawfulF2.Divariant[CapsBoth, CapsLeft, CapsRight, Any]
  }

  /**
   * Aliases of the `ZLaws` law constructors. The effectual ones (`Law1ZIO`,
   * `Law2ZIO`, `Law3ZIO`) need no environment (`R = Any`).
   */
  object Laws {

    /**
     * `ZLaws.Law1`: extend it to write a law.
     */
    type Law1[-Caps[_]] = ZLaws.Law1[Caps]

    /**
     * `ZLaws.Law1ZIO` with no environment (`R = Any`): extend it to write an
     * effectual law.
     */
    type Law1ZIO[-Caps[_]] = ZLaws.Law1ZIO[Caps, Any]

    /**
     * `ZLaws.Law2`: extend it to write a law.
     */
    type Law2[-Caps[_]] = ZLaws.Law2[Caps]

    /**
     * `ZLaws.Law2ZIO` with no environment (`R = Any`): extend it to write an
     * effectual law.
     */
    type Law2ZIO[-Caps[_]] = ZLaws.Law2ZIO[Caps, Any]

    /**
     * `ZLaws.Law3`: extend it to write a law.
     */
    type Law3[-Caps[_]] = ZLaws.Law3[Caps]

    /**
     * `ZLaws.Law3ZIO` with no environment (`R = Any`): extend it to write an
     * effectual law.
     */
    type Law3ZIO[-Caps[_]] = ZLaws.Law3ZIO[Caps, Any]
  }

  /**
   * Aliases of the `ZLaws2` law constructors.
   */
  object Laws2 {

    /**
     * `ZLaws2.Law1Left`: extend it to write a law.
     */
    type Law1Left[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_]] = ZLaws2.Law1Left[CapsBoth, CapsLeft, CapsRight]

    /**
     * `ZLaws2.Law1Right`: extend it to write a law.
     */
    type Law1Right[-CapsBoth[_, _], -CapsLeft[_], -CapsRight[_]] = ZLaws2.Law1Right[CapsBoth, CapsLeft, CapsRight]
  }

  /**
   * Aliases of the `ZLawsF` laws and law constructors whose laws need no
   * environment (`R = Any`).
   */
  object LawsF {

    /**
     * `ZLawsF.Covariant` with no environment (`R = Any`).
     */
    type Covariant[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant[CapsF, Caps, Any]

    /**
     * `ZLawsF.Contravariant` with no environment (`R = Any`).
     */
    type Contravariant[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant[CapsF, Caps, Any]

    /**
     * `ZLawsF.Invariant` with no environment (`R = Any`).
     */
    type Invariant[-CapsF[_[_]], -Caps[_]] = ZLawsF.Invariant[CapsF, Caps, Any]

    /**
     * Aliases of the `ZLawsF.Covariant` law constructors.
     */
    object Covariant {

      /**
       * `ZLawsF.Covariant.ComposeLaw`: extend it to write a law.
       */
      type ComposeLaw[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.ComposeLaw[CapsF, Caps]

      /**
       * `ZLawsF.Covariant.FlattenLaw`: extend it to write a law.
       */
      type FlattenLaw[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.FlattenLaw[CapsF, Caps]

      /**
       * `ZLawsF.Covariant.Law1`: extend it to write a law.
       */
      type Law1[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.Law1[CapsF, Caps]

      /**
       * `ZLawsF.Covariant.Law1ZIO` with no environment (`R = Any`): extend it
       * to write an effectual law.
       */
      type Law1ZIO[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.Law1ZIO[CapsF, Caps, Any]

      /**
       * `ZLawsF.Covariant.Law2`: extend it to write a law.
       */
      type Law2[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.Law2[CapsF, Caps]

      /**
       * `ZLawsF.Covariant.Law2ZIO` with no environment (`R = Any`): extend it
       * to write an effectual law.
       */
      type Law2ZIO[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.Law2ZIO[CapsF, Caps, Any]

      /**
       * `ZLawsF.Covariant.Law3`: extend it to write a law.
       */
      type Law3[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.Law3[CapsF, Caps]

      /**
       * `ZLawsF.Covariant.Law3ZIO` with no environment (`R = Any`): extend it
       * to write an effectual law.
       */
      type Law3ZIO[-CapsF[_[+_]], -Caps[_]] = ZLawsF.Covariant.Law3ZIO[CapsF, Caps, Any]
    }

    /**
     * Aliases of the `ZLawsF.Contravariant` law constructors.
     */
    object Contravariant {

      /**
       * `ZLawsF.Contravariant.ComposeLaw`: extend it to write a law.
       */
      type ComposeLaw[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant.ComposeLaw[CapsF, Caps]

      /**
       * `ZLawsF.Contravariant.Law1`: extend it to write a law.
       */
      type Law1[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant.Law1[CapsF, Caps]

      /**
       * `ZLawsF.Contravariant.Law1ZIO` with no environment (`R = Any`): extend
       * it to write an effectual law.
       */
      type Law1ZIO[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant.Law1ZIO[CapsF, Caps, Any]

      /**
       * `ZLawsF.Contravariant.Law2`: extend it to write a law.
       */
      type Law2[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant.Law2[CapsF, Caps]

      /**
       * `ZLawsF.Contravariant.Law2ZIO` with no environment (`R = Any`): extend
       * it to write an effectual law.
       */
      type Law2ZIO[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant.Law2ZIO[CapsF, Caps, Any]

      /**
       * `ZLawsF.Contravariant.Law3`: extend it to write a law.
       */
      type Law3[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant.Law3[CapsF, Caps]

      /**
       * `ZLawsF.Contravariant.Law3ZIO` with no environment (`R = Any`): extend
       * it to write an effectual law.
       */
      type Law3ZIO[-CapsF[_[-_]], -Caps[_]] = ZLawsF.Contravariant.Law3ZIO[CapsF, Caps, Any]
    }

    /**
     * Aliases of the `ZLawsF.Invariant` law constructors.
     */
    object Invariant {

      /**
       * `ZLawsF.Invariant.Law1`: extend it to write a law.
       */
      type Law1[-CapsF[_[_]], -Caps[_]] = ZLawsF.Invariant.Law1[CapsF, Caps]

      /**
       * `ZLawsF.Invariant.Law1ZIO` with no environment (`R = Any`): extend it
       * to write an effectual law.
       */
      type Law1ZIO[-CapsF[_[_]], -Caps[_]] = ZLawsF.Invariant.Law1ZIO[CapsF, Caps, Any]

      /**
       * `ZLawsF.Invariant.Law2`: extend it to write a law.
       */
      type Law2[-CapsF[_[_]], -Caps[_]] = ZLawsF.Invariant.Law2[CapsF, Caps]

      /**
       * `ZLawsF.Invariant.Law2ZIO` with no environment (`R = Any`): extend it
       * to write an effectual law.
       */
      type Law2ZIO[-CapsF[_[_]], -Caps[_]] = ZLawsF.Invariant.Law2ZIO[CapsF, Caps, Any]

      /**
       * `ZLawsF.Invariant.Law3`: extend it to write a law.
       */
      type Law3[-CapsF[_[_]], -Caps[_]] = ZLawsF.Invariant.Law3[CapsF, Caps]

      /**
       * `ZLawsF.Invariant.Law3ZIO` with no environment (`R = Any`): extend it
       * to write an effectual law.
       */
      type Law3ZIO[-CapsF[_[_]], -Caps[_]] = ZLawsF.Invariant.Law3ZIO[CapsF, Caps, Any]
    }
  }

  /**
   * Aliases of the `ZLawsF2` laws and law constructors whose laws need no
   * environment (`R = Any`).
   */
  object LawsF2 {

    /**
     * `ZLawsF2.Divariant` with no environment (`R = Any`).
     */
    type Divariant[-CapsBoth[_[-_, +_]], -CapsLeft[_], -CapsRight[_]] =
      ZLawsF2.Divariant[CapsBoth, CapsLeft, CapsRight, Any]

    /**
     * Aliases of the `ZLawsF2.Divariant` law constructors.
     */
    object Divariant {

      /**
       * `ZLawsF2.Divariant.ComposeLaw`: extend it to write a law.
       */
      type ComposeLaw[-CapsBothF[_[-_, +_]], -Caps[_]] = ZLawsF2.Divariant.ComposeLaw[CapsBothF, Caps]

      /**
       * `ZLawsF2.Divariant.Law1`: extend it to write a law.
       */
      type Law1[CapsBothF[_[-_, +_]], -CapsLeft[_], -CapsRight[_]] =
        ZLawsF2.Divariant.Law1[CapsBothF, CapsLeft, CapsRight]
    }
  }

  /**
   * Checks that values generated by the specified generator satisfy the laws of
   * the lawful instance, using the `Caps[A]` instance in implicit scope. It
   * runs `lawful.laws.run(gen)`: with laws built from the `ZLaws` constructors,
   * each law checks its own samples (200 by default) and stops at its first
   * failing sample. The effect does not fail; a failed law gives a failed
   * `TestResult`.
   *
   * {{{
   * import zio.test._
   * import zio.test.laws._
   *
   * trait Combine[A] {
   *   def combine(a1: A, a2: A): A
   * }
   *
   * object Combine extends Lawful[Combine] {
   *   val associativity: Laws.Law3[Combine] =
   *     new Laws.Law3[Combine]("associativity") {
   *       def apply[A: Combine](a1: A, a2: A, a3: A): TestResult = {
   *         val c = implicitly[Combine[A]]
   *         assertTrue(c.combine(c.combine(a1, a2), a3) == c.combine(a1, c.combine(a2, a3)))
   *       }
   *     }
   *
   *   val laws: Laws[Combine] = associativity
   * }
   *
   * implicit val intSum: Combine[Int] = new Combine[Int] {
   *   def combine(a1: Int, a2: Int): Int = a1 + a2
   * }
   *
   * object CombineSpec extends ZIOSpecDefault {
   *   def spec = test("integer addition is associative") {
   *     checkAllLaws(Combine)(Gen.int)
   *   }
   * }
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
   */
  def checkAllLaws[Caps[_], R, R1 <: R, A: Caps](
    lawful: ZLawful[Caps, R]
  )(gen: Gen[R1, A])(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
    lawful.laws.run(gen)

  /**
   * Checks that pairs of values generated by `a` and `b` satisfy the laws of
   * the lawful instance, using the `CapsBoth[A, B]`, `CapsLeft[A]` and
   * `CapsRight[B]` instances in implicit scope. It runs `lawful.laws.run(a,
   * b)`; the effect does not fail, and a failed law gives a failed
   * `TestResult`.
   */
  def checkAllLaws[CapsBoth[_, _], CapsLeft[_], CapsRight[_], R, R1 <: R, A: CapsLeft, B: CapsRight](
    lawful: ZLawful2[CapsBoth, CapsLeft, CapsRight, R]
  )(a: Gen[R1, A], b: Gen[R1, B])(implicit
    CapsBoth: CapsBoth[A, B],
    trace: Trace
  ): ZIO[R1, Nothing, TestResult] =
    lawful.laws.run(a, b)

  /**
   * Checks that `F[A]` values, built by `genF` from the generator `gen` of `A`
   * values, satisfy the laws of the lawful instance for the covariant type
   * constructor `F[+_]`. It runs `lawful.laws.run(genF, gen)`; the effect does
   * not fail, and a failed law gives a failed `TestResult`.
   */
  def checkAllLaws[CapsF[_[+_]], Caps[_], R, R1 <: R, F[+_]: CapsF, A: Caps](
    lawful: ZLawfulF.Covariant[CapsF, Caps, R]
  )(genF: GenF[R1, F], gen: Gen[R1, A])(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
    lawful.laws.run(genF, gen)

  /**
   * Checks that `F[A]` values, built by `genF` from the generator `gen` of `A`
   * values, satisfy the laws of the lawful instance for the contravariant type
   * constructor `F[-_]`. It runs `lawful.laws.run(genF, gen)`; the effect does
   * not fail, and a failed law gives a failed `TestResult`.
   */
  def checkAllLaws[CapsF[_[-_]], Caps[_], R, R1 <: R, F[-_]: CapsF, A: Caps](
    lawful: ZLawfulF.Contravariant[CapsF, Caps, R]
  )(genF: GenF[R1, F], gen: Gen[R1, A])(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
    lawful.laws.run(genF, gen)

  /**
   * Checks that `F[A]` values, built by `genF` from the generator `gen` of `A`
   * values, satisfy the laws of the lawful instance for the invariant type
   * constructor `F[_]`. It runs `lawful.laws.run(genF, gen)`; the effect does
   * not fail, and a failed law gives a failed `TestResult`.
   */
  def checkAllLaws[CapsF[_[_]], Caps[_], R, R1 <: R, F[_]: CapsF, A: Caps](
    lawful: ZLawfulF.Invariant[CapsF, Caps, R]
  )(genF: GenF[R1, F], gen: Gen[R1, A])(implicit trace: Trace): ZIO[R1, Nothing, TestResult] =
    lawful.laws.run(genF, gen)
}
