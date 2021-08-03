package zio.test

import zio.clock.Clock
import zio.{URIO, ZIO}
import zio.duration.Duration
import zio.test.AssertionResult.{FailureDetailsResult, TraceResult}
import zio.test.BoolAlgebra.Value
import zio.test.ExecutedSpec.{LabeledCase, MultipleCase, TestCase}
import zio.test.TestAspect.tag

abstract class SnapshotRunnableSpec extends DefaultRunnableSpec with FileIOPlatformSpecific {

  private[test] def snapshotFilePath(path: String, label: String): String = {
    val lastSlash = path.lastIndexOf('/')
    s"${path.substring(0, lastSlash)}/__snapshots__${path.substring(lastSlash + 1).stripSuffix(".scala")}/$label"
  }

  final def equalToSnapshot[A, B](expected: A)(implicit eql: Eql[A, B]): Assertion[B] =
    Assertion.assertion("equalToSnapshot")() { actual =>
      (actual, expected) match {
        case (left: Array[_], right: Array[_]) => left.sameElements[Any](right)
        case (left, right)                     => left == right
      }
    }

  def snapshotTest[Any, E >: Throwable](
    label: String
  )(result: String)(implicit sourceLocation: SourceLocation): ZSpec[Any, E] =
    snapshotTestM(label)(ZIO.succeed(result))(sourceLocation)

  def noSnapFileName(snapFileName: String, sourceLocation: SourceLocation): TestResult = {
    val noSnapFileAssertion = Assertion.assertion[String](s"No snapshot $snapFileName file found")()(_ => false)
    BoolAlgebra.failure(
      AssertionResult.FailureDetailsResult(
        FailureDetails(
          ::(
            AssertionValue(
              noSnapFileAssertion,
              "",
              noSnapFileAssertion.run(""),
              sourceLocation = Some(sourceLocation.path)
            ),
            Nil
          )
        )
      )
    )
  }

  val SNAPSHOT_TEST = "SNAPSHOT_TEST"

  def snapshotTestM[R, E >: Throwable](
    label: String
  )(result: ZIO[R, E, String])(implicit sourceLocation: SourceLocation): ZSpec[R, E] =
    testM(label)(
      for {
        snapFileName <- ZIO.succeed(snapshotFilePath(sourceLocation.path, label))
        actual       <- result
        existing     <- existsFile(snapFileName)
        res <- if (existing)
          readFile(snapFileName).map { (snapshot: String) =>
            // FIXME this shows nice error messages but I need to get correct value when fixing snapshots
//            assertTrue(actual == snapshot): TestResult
            CompileVariants.assertProxy(actual, snapshot, sourceLocation.path)(equalToSnapshot(snapshot))
          }
        else
          ZIO.succeed(noSnapFileName(snapFileName, sourceLocation))
      } yield res
    ) @@ tag(SNAPSHOT_TEST)

  class FixSnapshotsReporter(className: String) extends TestReporter[Failure] {

    override def apply(d: Duration, e: ExecutedSpec[Failure]): URIO[TestLogger, Unit] =
      fixSnapshot(e, None)

    def fixSnapshot(e: ExecutedSpec[Failure], label: Option[String]): URIO[TestLogger, Unit] =
      e.caseValue match {
        case LabeledCase(label, spec) => fixSnapshot(spec, Some(label))
        case MultipleCase(specs)      => ZIO.foreach_(specs)(fixSnapshot(_, None))
        case TestCase(Right(_), _) =>
          TestLogger.logLine(s"`${label.get}` passes, snapshot not updated")
        case TestCase(
        Left(TestFailure.Assertion(Value(FailureDetailsResult(FailureDetails(assertion :: Nil), None)))),
//        Left(TestFailure.Assertion(Value(TraceResult(t, None)))),
        _
        ) =>
          label match {
            case Some(name) =>
              writeFile(snapshotFilePath(assertion.sourceLocation.get, label.get), assertion.value.toString).orDie *>
                TestLogger.logLine(s"`$name` updated snapshot")
            case None =>
              TestLogger.logLine(s"No label for snapshot test")
          }
        case TestCase(Left(TestFailure.Assertion(a)), _) =>
          TestLogger.logLine(s"`${label.get}` weird state - should not happen - snapshot not updated\n$a\n\n\n")
        case TestCase(Left(TestFailure.Runtime(_)), _) =>
          TestLogger.logLine(s"`${label.get}` failed in runtime, snapshot not updated")
      }

  }

  /**
   * Fixes snapshot file or inline snapshot
   */
  private[zio] def fixSnapshot(
    spec: ZSpec[Environment, Failure],
    className: String
  ): URIO[TestLogger with Clock, ExecutedSpec[Failure]] =
    runner
      .withReporter(new FixSnapshotsReporter(className))
      .run(aspects.foldLeft(spec)(_ @@ _))

}
