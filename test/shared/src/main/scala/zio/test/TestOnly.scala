package zio.test

import zio.test.Spec.{ SuiteCase, TestCase }
import zio.{ Cause, ZIO }

object TestOnly {
  implicit class SpecOps[R, E, L, S](spec: ZSpec[R, E, L, S]) {
    def onlyWith(label: L): ZSpec[R, E, L, S] = only(_ == label)
    def only(p: L => Boolean): ZSpec[R, E, L, S] =
      process(spec, hasLabel(spec, p), p)

    private def hasLabel(spec: ZSpec[R, E, L, S], p: L => Boolean): ZIO[R, E, Boolean] =
      spec.caseValue match {
        case SuiteCase(label, specsZ, _) =>
          if (p(label)) ZIO.succeed(true)
          else
            specsZ
              .flatMap(
                specs =>
                  ZIO
                    .sequence(specs.map(hasLabel(_, p)))
                    .map(_.exists(identity))
              )
        case TestCase(label, _) => ZIO.succeed(p(label))
      }

    private def process(spec: ZSpec[R, E, L, S], hasLabel: ZIO[R, E, Boolean], p: L => Boolean): ZSpec[R, E, L, S] = {
      val failingTestEffect =
        ZIO.succeed(Left(TestFailure.Runtime(Cause.die(new RuntimeException("No matching label found")))))

      def transformTest(label: L, test: ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]) =
        if (p(label)) test else ZIO.succeed(Right(TestSuccess.Ignored))

      def transformSuite(
        label: L,
        specs: ZIO[R, E, Vector[Spec[R, E, L, Either[TestFailure[Nothing], TestSuccess[S]]]]]
      ) =
        if (p(label)) specs else specs.map(_.map(process(_, hasLabel, p)))

      spec.copy(caseValue = spec.caseValue match {
        case TestCase(label, test) =>
          TestCase(label, hasLabel.flatMap { found =>
            if (!found) failingTestEffect
            else {
              transformTest(label, test)
            }
          })
        case SuiteCase(label, specs, exec) =>
          SuiteCase(
            label,
            hasLabel.flatMap(
              found =>
                if (!found) ZIO.succeed(Vector(Spec(TestCase(label, failingTestEffect))))
                else transformSuite(label, specs)
            ),
            exec
          )
      })
    }
  }
}
