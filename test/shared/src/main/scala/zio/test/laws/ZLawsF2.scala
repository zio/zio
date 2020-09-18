package zio.test.laws

import zio.ZIO
import zio.test.{ Gen, TestConfig, TestResult }

object ZLawsF2 {

  /**
   * `ZLawsF2` for Divariant type constructors.
   */
  abstract class Divariant[-CapsF[_[-_, +_]], -CapsLeft[_], -CapsRight[_], -R] { self =>

    /**
     * Test that values of type `F[+_,-_]` satisfy the laws using the specified
     * function to construct a generator of `F[A,B]` values given a generator of
     * `B` values.
     */
    def run[R1 <: R with TestConfig, F[-_, +_]: CapsF, A: CapsLeft, B: CapsRight](
      genF: GenF2[R1, F],
      gen: Gen[R1, B]
    ): ZIO[R1, Nothing, TestResult]

    /**
     * Combine these laws with the specified laws to produce a set of laws that
     * require both sets of laws to be satisfied.
     */
    def +[CapsF1[x[-_, +_]] <: CapsF[x], CapsLeft1[x] <: CapsLeft[x], CapsRight1[x] <: CapsRight[x], R1 <: R](
      that: Divariant[CapsF1, CapsLeft1, CapsRight1, R1]
    ): Divariant[CapsF1, CapsLeft1, CapsRight1, R1] =
      Divariant.Both(self, that)
  }

  object Divariant {

    private final case class Both[-CapsBothF[_[-_, +_]], -CapsLeft[_], -CapsRight[_], -R](
      left: Divariant[CapsBothF, CapsLeft, CapsRight, R],
      right: Divariant[CapsBothF, CapsLeft, CapsRight, R]
    ) extends Divariant[CapsBothF, CapsLeft, CapsRight, R] {

      override final def run[R1 <: R with TestConfig, F[-_, +_]: CapsBothF, A: CapsLeft, B: CapsRight](
        genF: GenF2[R1, F],
        gen: Gen[R1, B]
      ): ZIO[R1, Nothing, TestResult] = {
        val lhs: ZIO[R1, Nothing, TestResult] = left.run(genF, gen)
        val rhs: ZIO[R1, Nothing, TestResult] = right.run(genF, gen)
        lhs.zipWith(rhs)(_ && _)
      }
    }

    // TODO 4211 laws for type class'es with 2 params differs significantly from tc with type constructor param with 1 hole
    // TODO needs to understand what Covariant/Contravariant laws and transform this for Divariant ... challange!
//    /**
//     * Constructs a law from a pure function taking a single parameter.
//     */
//    abstract class Law1[-CapsF[_[-_, +_]], -Caps[_]](label: String) extends Divariant[CapsF, Caps, Any] {
//      def apply[F[-_, +_]: CapsF, A: Caps, B: Caps](fa: F[A, B]): TestResult
//
//      override final def run[R1 <: TestConfig, F[-_, +_]: CapsF, A, B: Caps](
//        genF: GenF2[R1, F],
//        gen: Gen[R1, B]
//      ): URIO[R1, TestResult] =
//        check(genF(gen): Gen[R1, F[A, B]])(apply(_).map(_.label(label)))
//    }
//
//    /**
//     * Constructs a law from an effectual function taking a single parameter.
//     */
//    abstract class Law1M[-CapsF[_[-_, +_]], -Caps[_], -R](label: String) extends Divariant[CapsF, Caps, R] {
//      def apply[F[-_, +_]: CapsF, A: Caps, B: Caps](fa: F[A, B]): URIO[R, TestResult]
//
//      override final def run[R1 <: R with TestConfig, F[-_, +_]: CapsF, A, B: Caps](
//        genF: GenF2[R1, F],
//        gen: Gen[R1, B]
//      ): ZIO[R1, Nothing, TestResult] =
//        checkM(genF(gen): Gen[R1, F[A, B]])(apply(_).map(_.map(_.label(label))))
//    }
//
//    /**
//     * Constructs a law from a pure function taking two parameters.
//     */
//    abstract class Law2[-CapsF[_[-_, +_]], -Caps[_]](label: String) extends Divariant[CapsF, Caps, Any] { self =>
//      def apply[F[-_, +_]: CapsF, A: Caps, A2: Caps, B: Caps, B2: Caps](fa: F[A, A2], fb: F[B, B2]): TestResult
//
//      override final def run[R <: TestConfig, F[-_, +_]: CapsF, A: Caps, B: Caps](
//        genF: GenF2[R, F],
//        gen: Gen[R, B]
//      ): URIO[R, TestResult] =
//        check(genF(gen): Gen[R, F[A, B]], genF(gen): Gen[R, F[A, B]])(apply(_, _).map(_.label(label)))
//    }
//
//    /**
//     * Constructs a law from an effectual function taking two parameters.
//     */
//    abstract class Law2M[-CapsF[_[-_, +_]], -Caps[_], -R](label: String) extends Divariant[CapsF, Caps, R] { self =>
//      def apply[F[-_, +_]: CapsF, A: Caps, A2: Caps, B: Caps, B2: Caps](
//        fa: F[A, A2],
//        fb: F[B, B2]
//      ): URIO[R, TestResult]
//
//      override final def run[R1 <: R with TestConfig, F[-_, +_]: CapsF, A: Caps, B: Caps](
//        genF: GenF2[R1, F],
//        gen: Gen[R1, B]
//      ): ZIO[R1, Nothing, TestResult] =
//        checkM(genF(gen): Gen[R1, F[A, B]], genF(gen): Gen[R1, F[A, B]])(apply(_, _).map(_.map(_.label(label))))
//    }
//
//    /**
//     * Constructs a law from a pure function taking three parameters.
//     */
//    abstract class Law3[-CapsF[_[-_, +_]], -Caps[_]](label: String) extends Divariant[CapsF, Caps, Any] { self =>
//      def apply[F[-_, +_]: CapsF, A: Caps, A2: Caps, B: Caps, B2: Caps, C: Caps, C2: Caps](
//        fa: F[A, A2],
//        fb: F[B, B2],
//        fc: F[C, C2]
//      ): TestResult
//
//      override final def run[R <: TestConfig, F[-_, +_]: CapsF, A: Caps, B: Caps](
//        genF: GenF2[R, F],
//        gen: Gen[R, B]
//      ): URIO[R, TestResult] =
//        check(genF(gen): Gen[R, F[A, B]], genF(gen): Gen[R, F[A, B]], genF(gen): Gen[R, F[A, B]])(
//          apply(_, _, _).map(_.label(label))
//        )
//    }
//
//    /**
//     * Constructs a law from an effectual function taking three parameters.
//     */
//    abstract class Law3M[-CapsF[_[-_, +_]], -Caps[_], -R](label: String) extends Divariant[CapsF, Caps, R] { self =>
//      def apply[F[-_, +_]: CapsF, A: Caps, A2: Caps, B: Caps, B2: Caps, C: Caps, C2: Caps](
//        fa: F[A, A2],
//        fb: F[B, B2],
//        fc: F[C, C2]
//      ): URIO[R, TestResult]
//      final def run[R1 <: R with TestConfig, F[-_, +_]: CapsF, A: Caps, B: Caps](
//        genF: GenF2[R1, F],
//        gen: Gen[R1, B]
//      ): ZIO[R1, Nothing, TestResult] =
//        checkM(genF(gen): Gen[R1, F[A, B]], genF(gen): Gen[R1, F[A, B]], genF(gen): Gen[R1, F[A, B]])(
//          apply(_, _, _).map(_.map(_.label(label)))
//        )
//    }
  }
}
