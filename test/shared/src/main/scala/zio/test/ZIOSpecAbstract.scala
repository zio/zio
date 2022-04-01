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

import org.portablescala.reflect.annotation.EnableReflectiveInstantiation
import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render._

@EnableReflectiveInstantiation
abstract class ZIOSpecAbstract extends ZIOApp {
  self =>

  def spec: ZSpec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any]

  type Failure

  def aspects: Chunk[TestAspectAtLeastR[Environment with TestEnvironment with ZIOAppArgs]] =
    Chunk(TestAspect.fibers)

  def testReporter(testRenderer: TestRenderer, testAnnotationRenderer: TestAnnotationRenderer)(implicit
    trace: ZTraceElement
  ): TestReporter[Any] =
    DefaultTestReporter(testRenderer, testAnnotationRenderer)

  final def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
    implicit val trace = Tracer.newTrace

    runSpec.provideSomeLayer[ZIOAppArgs with Scope](
      ZLayer.environment[ZIOAppArgs with Scope] +!+
        (ZEnv.live >>> TestEnvironment.live +!+ layer +!+ TestLogger.fromConsole)
    )
  }

  final def <>(that: ZIOSpecAbstract)(implicit trace: ZTraceElement): ZIOSpecAbstract =
    new ZIOSpecAbstract {
      type Environment = self.Environment with that.Environment

      def layer: ZLayer[ZIOAppArgs with Scope, Any, Environment] =
        self.layer +!+ that.layer

      override def runSpec: ZIO[
        Environment with TestEnvironment with ZIOAppArgs with Scope,
        Any,
        Any
      ] =
        self.runSpec.zipPar(that.runSpec)

      def spec: ZSpec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any] =
        self.spec + that.spec

      def tag: EnvironmentTag[Environment] = {
        implicit val selfTag: EnvironmentTag[self.Environment] = self.tag
        implicit val thatTag: EnvironmentTag[that.Environment] = that.tag
        val _                                                  = (selfTag, thatTag)
        EnvironmentTag[Environment]
      }
    }

  protected def runSpec: ZIO[
    Environment with TestEnvironment with ZIOAppArgs with Scope,
    Any,
    Any
  ] = {
    implicit val trace = Tracer.newTrace
    for {
      args    <- ZIO.service[ZIOAppArgs]
      testArgs = TestArgs.parse(args.getArgs.toArray)
      summary <- runSpec(spec, testArgs)
      exitCode = if (summary.fail > 0) 1 else 0
      _       <- doExit(exitCode)
    } yield ()
  }

  private def createTestReporter(rendererName: String)(implicit trace: ZTraceElement): TestReporter[Any] = {
    val renderer = rendererName match {
      case "intellij" => IntelliJRenderer
      case _          => TestRenderer.default
    }
    testReporter(renderer, TestAnnotationRenderer.default)
  }

  private def doExit(exitCode: Int)(implicit trace: ZTraceElement): UIO[Unit] =
    if (TestPlatform.isJVM) {
      ZIO.succeed(
        try if (!isAmmonite) sys.exit(exitCode)
        catch { case _: SecurityException => }
      )
    } else {
      UIO.unit
    }

  private def isAmmonite: Boolean =
    sys.env.exists { case (k, v) =>
      k.contains("JAVA_MAIN_CLASS") && v == "ammonite.Main"
    }

  private[zio] def runSpec(
    spec: ZSpec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any],
    testArgs: TestArgs
  )(implicit
    trace: ZTraceElement
  ): URIO[
    Environment with TestEnvironment with ZIOAppArgs with Scope,
    Summary
  ] = {
    val filteredSpec = FilteredSpec(spec, testArgs)

    for {
      runtime <-
        ZIO.runtime[
          Environment with TestEnvironment with ZIOAppArgs with Scope
        ]
      environment   = runtime.environment
      runtimeConfig = hook(runtime.runtimeConfig)
      runner =
        TestRunner(
          TestExecutor
            .default[
              Environment with TestEnvironment with ZIOAppArgs with Scope,
              Any
            ](
              ZLayer.succeedEnvironment(environment) +!+ (Scope.default >>> testEnvironment),
              (Console.live >>> TestLogger.fromConsole >>> ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live)
            ),
          runtimeConfig
        )
      testReporter = testArgs.testRenderer.fold(runner.reporter)(createTestReporter)
      summary <-
        runner.withReporter(testReporter).run(aspects.foldLeft(filteredSpec)(_ @@ _))
    } yield summary
  }.onInterrupt(ZIO.debug("ZIOSpecAbstract.runSpec interrupted"))

}
