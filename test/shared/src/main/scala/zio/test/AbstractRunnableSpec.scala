/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import org.portablescala.reflect.annotation.EnableReflectiveInstantiation
import zio.clock.Clock
import zio.duration.Duration
import zio.test.AssertionResult.FailureDetailsResult
import zio.test.BoolAlgebra.Value
import zio.test.ExecutedSpec._
import zio.{Has, URIO, ZIO}

@EnableReflectiveInstantiation
abstract class AbstractRunnableSpec extends FileIOPlatformSpecific {

  type Environment <: Has[_]
  type Failure

  def aspects: List[TestAspect[Nothing, Environment, Nothing, Any]]
  def runner: TestRunner[Environment, Failure]
  def spec: ZSpec[Environment, Failure]

  /**
   * Returns an effect that executes the spec, producing the results of the execution.
   */
  final def run: URIO[TestLogger with Clock, ExecutedSpec[Failure]] =
    runSpec(spec)

  private[test] def snapshotFilePath(path: String, label: String): String = {
    println("path", path)
    println("label", label)
    val lastSlash = path.lastIndexOf('/')
    s"${path.substring(0, lastSlash)}/__snapshots__${path.substring(lastSlash + 1).stripSuffix(".scala")}/$label"
  }

  /**
   * Returns an effect that executes a given spec, producing the results of the execution.
   */
  private[zio] def runSpec(
    spec: ZSpec[Environment, Failure]
  ): URIO[TestLogger with Clock, ExecutedSpec[Failure]] =
    runner.run(aspects.foldLeft(spec)(_ @@ _))

  class FixSnapshotsReporter(className: String) extends TestReporter[Failure] {

    override def apply(d: Duration, e: ExecutedSpec[Failure]): URIO[TestLogger, Unit] =
      fixSnapshot(e, None)

    def fixSnapshot(e: ExecutedSpec[Failure], label: Option[String]): URIO[TestLogger, Unit] =
      e.caseValue match {
        case LabeledCase(label, spec) => fixSnapshot(spec, Some(label))
        case MultipleCase(specs)      => ZIO.foreach_(specs)(fixSnapshot(_, None))
        case TestCase(Right(_), _) =>
          TestLogger.logLine(s"Test $label passes, snapshot not updated")
        case TestCase(
              Left(TestFailure.Assertion(Value(FailureDetailsResult(FailureDetails(assertion :: Nil), None)))),
              _
            ) =>
          label match {
            case Some(name) =>
              writeFile(snapshotFilePath(assertion.sourceLocation.get, label.get), assertion.value.toString).orDie *>
                TestLogger.logLine(s"$name updated snapshot")
            case None =>
              TestLogger.logLine(s"No label for snapshot test")
          }
        case TestCase(Left(TestFailure.Assertion(_)), _) =>
          TestLogger.logLine(s"$label weird state - should not happen - snapshot not updated")
        case TestCase(Left(TestFailure.Runtime(_)), _) =>
          TestLogger.logLine(s"Test $label failed in runtime, snapshot not updated")
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

  /**
   * the platform used by the runner
   */
  final def platform = runner.platform
}
