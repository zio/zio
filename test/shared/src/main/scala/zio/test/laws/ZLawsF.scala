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

package zio.test.laws

import zio.ZIO
import zio.random.Random
import zio.test.{ check, checkM, Gen, TestResult }

object ZLawsF {

  sealed trait Covariant[-Caps[_[+_]], -R] { self =>

    /**
     * Test that values of type `F[+_]` satisfy the laws using the specified
     * function to construct a generator of `F[A]` values given a generator of
     * `A` values.
     */
    def run[R1 <: R with Random, F[+_]: Caps](genF: GenF[R1, F]): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that
     * require both sets of laws to be satisfied.
     */
    def +[Caps1[x[+_]] <: Caps[x], R1 <: R](that: Covariant[Caps1, R1]): Covariant[Caps1, R1] =
      Covariant.Both(self, that)
  }

  object Covariant {

    private final case class Both[-Caps[_[+_]], -R](left: Covariant[Caps, R], right: Covariant[Caps, R])
        extends Covariant[Caps, R] {
      final def run[R1 <: R with Random, F[+_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        left.run(gen).zipWith(right.run(gen))(_ && _)
    }

    /**
     * Constructs a law from a pure function taking a single parameter.
     */
    abstract class Law1[-Caps[_[+_]]](label: String) extends Covariant[Caps, Any] { self =>
      def apply[F[+_]: Caps, A](fa: F[A]): TestResult
      final def run[R <: Random, F[+_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt))(apply(_).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking a single parameter.
     */
    abstract class Law1M[-Caps[_[+_]], -R](label: String) extends Covariant[Caps, R] { self =>
      def apply[F[+_]: Caps, A](fa: F[A]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[+_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt))(apply(_).map(_.map(_.label(label))))
    }

    /**
     * Constructs a law from a pure function taking two parameters.
     */
    abstract class Law2[-Caps[_[+_]]](label: String) extends Covariant[Caps, Any] { self =>
      def apply[F[+_]: Caps, A, B](fa: F[A], fb: F[B]): TestResult
      final def run[R <: Random, F[+_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking two parameters.
     */
    abstract class Law2M[-Caps[_[+_]], -R](label: String) extends Covariant[Caps, R] { self =>
      def apply[F[+_]: Caps, A, B](fa: F[A], fb: F[B]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[+_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _).map(_.map(_.label(label))))
    }

    /**
     * Constructs a law from a pure function taking three parameters.
     */
    abstract class Law3[-Caps[_[+_]]](label: String) extends Covariant[Caps, Any] { self =>
      def apply[F[+_]: Caps, A, B, C](fa: F[A], fb: F[B], fc: F[C]): TestResult
      final def run[R <: Random, F[+_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt), gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _, _).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking three parameters.
     */
    abstract class Law3M[-Caps[_[+_]], -R](label: String) extends Covariant[Caps, R] { self =>
      def apply[F[+_]: Caps, A, B, C](fa: F[A], fb: F[B], fc: F[C]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[+_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt), gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _, _).map(_.map(_.label(label))))
    }

    /**
     * Constructs a law from a function from generators of arbitrary values to
     * a test result.
     */
    abstract class Law3Gen[-Caps[_[+_]], -R](label: String) extends Covariant[Caps, R] { self =>
      def apply[F[+_]: Caps, R1 <: R, A, B, C](a: Gen[R1, A], b: Gen[R1, B], c: Gen[R1, C])(
        gen: GenF[R1, F]
      ): ZIO[R1, Nothing, TestResult]
      final def run[R1 <: R with Random, F[+_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        apply(gen(Gen.anyInt), gen(Gen.anyInt), gen(Gen.anyInt))(gen).map(_.map(_.label(label)))
    }

    /**
     * Constructs a law from a function that takes an arbitrary initial `F[A]`
     * and two arbitrary functions `f` and `g`.
     */
    abstract class Law3Specialized[-Caps[_[+_]]](label: String) extends Covariant[Caps, Any] { self =>
      def apply[F[+_]: Caps, A, B, C](fa: F[A], f: A => B, g: B => C): TestResult
      final def run[R <: Random, F[+_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt), Gen.function(Gen.anyInt), Gen.function(Gen.anyInt))(apply(_, _, _).map(_.label(label)))
    }
  }

  sealed trait Contravariant[-Caps[_[-_]], -R] { self =>

    /**
     * Test that values of type `F[-_]` satisfy the laws using the specified
     * function to construct a generator of `F[A]` values given a generator of
     * `A` values.
     */
    def run[R1 <: R with Random, F[-_]: Caps](genF: GenF[R1, F]): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that
     * require both sets of laws to be satisfied.
     */
    def +[Caps1[x[-_]] <: Caps[x], R1 <: R](that: Contravariant[Caps1, R1]): Contravariant[Caps1, R1] =
      Contravariant.Both(self, that)
  }

  object Contravariant {

    private final case class Both[-Caps[_[-_]], -R](left: Contravariant[Caps, R], right: Contravariant[Caps, R])
        extends Contravariant[Caps, R] {
      final def run[R1 <: R with Random, F[-_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        left.run(gen).zipWith(right.run(gen))(_ && _)
    }

    /**
     * Constructs a law from a pure function taking a single parameter.
     */
    abstract class Law1[-Caps[_[-_]]](label: String) extends Contravariant[Caps, Any] { self =>
      def apply[F[-_]: Caps, A](fa: F[A]): TestResult
      final def run[R <: Random, F[-_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt))(apply(_).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking a single parameter.
     */
    abstract class Law1M[-Caps[_[-_]], -R](label: String) extends Contravariant[Caps, R] { self =>
      def apply[F[-_]: Caps, A](fa: F[A]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[-_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt))(apply(_).map(_.map(_.label(label))))
    }

    /**
     * Constructs a law from a pure function taking two parameters.
     */
    abstract class Law2[-Caps[_[-_]]](label: String) extends Contravariant[Caps, Any] { self =>
      def apply[F[-_]: Caps, A, B](fa: F[A], fb: F[B]): TestResult
      final def run[R <: Random, F[-_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking two parameters.
     */
    abstract class Law2M[-Caps[_[-_]], -R](label: String) extends Contravariant[Caps, R] { self =>
      def apply[F[-_]: Caps, A, B](fa: F[A], fb: F[B]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[-_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _).map(_.map(_.label(label))))
    }

    /**
     * Constructs a law from a pure function taking three parameters.
     */
    abstract class Law3[-Caps[_[-_]]](label: String) extends Contravariant[Caps, Any] { self =>
      def apply[F[-_]: Caps, A, B, C](fa: F[A], fb: F[B], fc: F[C]): TestResult
      final def run[R <: Random, F[-_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt), gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _, _).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking three parameters.
     */
    abstract class Law3M[-Caps[_[-_]], -R](label: String) extends Contravariant[Caps, R] { self =>
      def apply[F[-_]: Caps, A, B, C](fa: F[A], fb: F[B], fc: F[C]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[-_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt), gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _, _).map(_.map(_.label(label))))
    }
  }

  sealed trait Invariant[-Caps[_[_]], -R] { self =>

    /**
     * Test that values of type `F[_]` satisfy the laws using the specified
     * function to construct a generator of `F[A]` values given a generator of
     * `A` values.
     */
    def run[R1 <: R with Random, F[_]: Caps](genF: GenF[R1, F]): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that
     * require both sets of laws to be satisfied.
     */
    def +[Caps1[x[_]] <: Caps[x], R1 <: R](that: Invariant[Caps1, R1]): Invariant[Caps1, R1] =
      Invariant.Both(self, that)
  }

  object Invariant {

    private final case class Both[-Caps[_[_]], -R](left: Invariant[Caps, R], right: Invariant[Caps, R])
        extends Invariant[Caps, R] {
      final def run[R1 <: R with Random, F[_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        left.run(gen).zipWith(right.run(gen))(_ && _)
    }

    /**
     * Constructs a law from a pure function taking a single parameter.
     */
    abstract class Law1[-Caps[_[_]]](label: String) extends Invariant[Caps, Any] { self =>
      def apply[F[_]: Caps, A](fa: F[A]): TestResult
      final def run[R <: Random, F[_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt))(apply(_).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking a single parameter.
     */
    abstract class Law1M[-Caps[_[_]], -R](label: String) extends Invariant[Caps, R] { self =>
      def apply[F[_]: Caps, A](fa: F[A]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt))(apply(_).map(_.map(_.label(label))))
    }

    /**
     * Constructs a law from a pure function taking two parameters.
     */
    abstract class Law2[-Caps[_[_]]](label: String) extends Invariant[Caps, Any] { self =>
      def apply[F[_]: Caps, A, B](fa: F[A], fb: F[B]): TestResult
      final def run[R <: Random, F[_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking two parameters.
     */
    abstract class Law2M[-Caps[_[_]], -R](label: String) extends Invariant[Caps, R] { self =>
      def apply[F[_]: Caps, A, B](fa: F[A], fb: F[B]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _).map(_.map(_.label(label))))
    }

    /**
     * Constructs a law from a pure function taking three parameters.
     */
    abstract class Law3[-Caps[_[_]]](label: String) extends Invariant[Caps, Any] { self =>
      def apply[F[_]: Caps, A, B, C](fa: F[A], fb: F[B], fc: F[C]): TestResult
      final def run[R <: Random, F[_]: Caps](gen: GenF[R, F]): ZIO[R, Nothing, TestResult] =
        check(gen(Gen.anyInt), gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _, _).map(_.label(label)))
    }

    /**
     * Constructs a law from an effectual function taking three parameters.
     */
    abstract class Law3M[-Caps[_[_]], -R](label: String) extends Invariant[Caps, R] { self =>
      def apply[F[_]: Caps, A, B, C](fa: F[A], fb: F[B], fc: F[C]): ZIO[R, Nothing, TestResult]
      final def run[R1 <: R with Random, F[_]: Caps](gen: GenF[R1, F]): ZIO[R1, Nothing, TestResult] =
        checkM(gen(Gen.anyInt), gen(Gen.anyInt), gen(Gen.anyInt))(apply(_, _, _).map(_.map(_.label(label))))
    }
  }
}
