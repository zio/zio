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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.environment.TestEnvironment

/**
 * A default runnable spec that provides testable versions of all of the
 * modules in ZIO (Clock, Has[Random], etc).
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
  )(implicit trace: ZTraceElement): URIO[Has[TestLogger] with Has[Clock], ExecutedSpec[Failure]] =
    runner.run(aspects.foldLeft(spec)(_ @@ _) @@ TestAspect.fibers)

  /**
   * Builds a suite containing a number of other specs.
   */
  def suite[In](label: String)(specs: In*)(implicit
    suiteConstructor: SuiteConstructor[In],
    trace: ZTraceElement
  ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError, suiteConstructor.OutSuccess] =
    zio.test.suite(label)(specs: _*)

  /**
   * Builds an effectual suite containing a number of other specs.
   */
  @deprecated("use suite", "2.0.0")
  def suiteM[R, E, T](label: String)(specs: ZIO[R, E, Iterable[Spec[R, E, T]]])(implicit
    trace: ZTraceElement
  ): Spec[R, E, T] =
    zio.test.suiteM(label)(specs)

  /**
   * Builds a spec with a single test.
   */
  def test[In](label: String)(
    assertion: => In
  )(implicit
    testConstructor: TestConstructor[Nothing, In],
    sourceLocation: SourceLocation,
    trace: ZTraceElement
  ): testConstructor.Out =
    zio.test.test(label)(assertion)
}
