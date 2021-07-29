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

import zio.clock.Clock
import zio.duration._
import zio.test.TestAspect.tag
import zio.test.environment.TestEnvironment
import zio.{URIO, ZIO}

/**
 * A default runnable spec that provides testable versions of all of the
 * modules in ZIO (Clock, Random, etc).
 */
abstract class DefaultRunnableSpec extends RunnableSpec[TestEnvironment, Any] {

  override def aspects: List[TestAspect[Nothing, TestEnvironment, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[TestEnvironment, Any] =
    defaultTestRunner

  /**
   * Returns an effect that executes a given spec, producing the results of the execution.
   */
  private[zio] override def runSpec(
    spec: ZSpec[Environment, Failure]
  ): URIO[TestLogger with Clock, ExecutedSpec[Failure]] =
    runner.run(aspects.foldLeft(spec)(_ @@ _) @@ TestAspect.fibers)

  /**
   * Builds a suite containing a number of other specs.
   */
  def suite[R, E, T](label: String)(specs: Spec[R, E, T]*): Spec[R, E, T] =
    zio.test.suite(label)(specs: _*)

  /**
   * Builds an effectual suite containing a number of other specs.
   */
  def suiteM[R, E, T](label: String)(specs: ZIO[R, E, Iterable[Spec[R, E, T]]]): Spec[R, E, T] =
    zio.test.suiteM(label)(specs)

  /**
   * Builds a spec with a single pure test.
   */
  def test(label: String)(assertion: => TestResult)(implicit loc: SourceLocation): ZSpec[Any, Nothing] =
    zio.test.test(label)(assertion)

  /**
   * Builds a spec with a single effectful test.
   */
  def testM[R, E](label: String)(assertion: => ZIO[R, E, TestResult])(implicit loc: SourceLocation): ZSpec[R, E] =
    zio.test.testM(label)(assertion)

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
                   CompileVariants.assertProxy(actual, snapshot, sourceLocation.path)(equalToSnapshot(snapshot))
                 }
               else
                 ZIO.succeed(noSnapFileName(snapFileName, sourceLocation))
      } yield res
    ) @@ tag(SNAPSHOT_TEST)

}
