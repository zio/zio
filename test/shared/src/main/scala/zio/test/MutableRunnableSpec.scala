/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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

import izumi.reflect.Tag
import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.util.control.NoStackTrace

/**
 * Syntax for writing test like
 * {{{
 * object MySpec extends MutableRunnableSpec(layer, aspect) {
 *   suite("foo") {
 *     test("name") {
 *     } @@ ignore
 *
 *     test("name 2")
 *   }
 *   suite("another suite") {
 *     test("name 3")
 *   }
 * }
 * }}}
 */
@deprecated("use RunnableSpec", "2.0.0")
class MutableRunnableSpec[R: Tag](
  layer: ZLayer[TestEnvironment, Throwable, R],
  aspect: TestAspect[R with TestEnvironment, R with TestEnvironment, Any, Any] = TestAspect.identity
) extends RunnableSpec[TestEnvironment, Any] {
  self =>

  private class InAnotherTestException(`type`: String, label: String)
      extends Exception(s"${`type`} `$label` is in another test")
      with NoStackTrace

  sealed trait SpecBuilder {
    def toSpec: ZSpec[R with TestEnvironment, Any]
    def label: String
  }

  sealed case class SuiteBuilder(label: String) extends SpecBuilder {

    private[test] var nested: Chunk[SpecBuilder] = Chunk.empty
    private var aspects: Chunk[TestAspect[R with TestEnvironment, R with TestEnvironment, Failure, Failure]] =
      Chunk.empty

    /**
     * Syntax for adding aspects.
     * {{{
     * test("foo") { assert(42, equalTo(42)) } @@ ignore
     * }}}
     */
    final def @@(
      aspect: TestAspect[R with TestEnvironment, R with TestEnvironment, Failure, Failure]
    )(implicit trace: ZTraceElement): SuiteBuilder = {
      aspects = aspects :+ aspect
      this
    }

    def toSpec: ZSpec[R with TestEnvironment, Any] = {
      implicit val trace = Tracer.newTrace
      aspects.foldLeft(
        zio.test.suite(label)(
          nested.map(_.toSpec): _*
        )
      )((spec, aspect) => spec @@ aspect)
    }
  }

  sealed case class TestBuilder(label: String, var toSpec: ZSpec[R with TestEnvironment, Any]) extends SpecBuilder {

    /**
     * Syntax for adding aspects.
     * {{{
     * test("foo") { assert(42, equalTo(42)) } @@ ignore
     * }}}
     */
    final def @@(
      aspect: TestAspect[R with TestEnvironment, R with TestEnvironment, Failure, Failure]
    )(implicit trace: ZTraceElement): TestBuilder = {
      toSpec = toSpec @@ aspect
      this
    }
  }

  // init SpecBuilder for this test class
  private var stack: List[SuiteBuilder] = SuiteBuilder(self.getClass.getSimpleName.stripSuffix("$")) :: Nil
  // to prevent calling tests inside tests
  // there should be no tests constructions after spec was built
  private var specBuilt = false

  /**
   * Builds a suite containing a number of other specs.
   */
  final def suite(label: String)(specs: => SpecBuilder): SuiteBuilder = {
    if (specBuilt)
      throw new InAnotherTestException("Suite", label)
    val _oldStack = stack
    val builder   = SuiteBuilder(label)
    stack.head.nested = stack.head.nested :+ builder
    stack = builder :: stack
    specs
    stack = _oldStack
    builder
  }

  /**
   * Builds a spec with a single test.
   */
  final def test[In](label: String)(assertion: => In)(implicit
    testConstructor: TestConstructor[R with TestEnvironment, In],
    trace: ZTraceElement
  ): TestBuilder = {
    if (specBuilt)
      throw new InAnotherTestException("Test", label)
    val test    = zio.test.test(label)(assertion)
    val builder = TestBuilder(label, test)
    stack.head.nested = stack.head.nested :+ builder
    builder
  }

  /**
   * Builds a spec with a single effectful test.
   */
  @deprecated("use test", "2.0.0")
  final def testM(
    label: String
  )(
    assertion: => ZIO[R with TestEnvironment, Failure, TestResult]
  )(implicit trace: ZTraceElement): TestBuilder =
    test(label)(assertion)

  final override def spec: ZSpec[Environment, Failure] = {
    implicit val trace = Tracer.newTrace
    specBuilt = true
    (stack.head @@ aspect).toSpec.provideCustomLayerShared(layer.mapError(TestFailure.fail))
  }

  override def aspects: List[TestAspectAtLeastR[TestEnvironment]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[TestEnvironment, Any] =
    defaultTestRunner

  /**
   * Returns an effect that executes a given spec, producing the results of the
   * execution.
   */
  private[zio] override def runSpec(
    spec: ZSpec[Environment, Failure]
  )(implicit
    trace: ZTraceElement
  ): URIO[
    Clock with ExecutionEventSink with Random,
    Summary
  ] =
    runner.run(aspects.foldLeft(spec)(_ @@ _) @@ TestAspect.fibers)
}
