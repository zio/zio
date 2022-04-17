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

  def spec: Spec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any]

  def aspects: Chunk[TestAspectAtLeastR[Environment with TestEnvironment with ZIOAppArgs]] =
    Chunk(TestAspect.fibers)

  def testReporter(testRenderer: TestRenderer, testAnnotationRenderer: TestAnnotationRenderer)(implicit
    trace: ZTraceElement
  ): TestReporter[Any] =
    DefaultTestReporter(testRenderer, testAnnotationRenderer)

  final def run: ZIO[ZIOAppArgs with Scope, String, Summary] = {
    implicit val trace = ZTraceElement.empty

    for {
      summary <- runSpec.provideSomeLayer[ZIOAppArgs with Scope] (
        (ZLayer.environment[ZIOAppArgs with Scope] +!+
        (ZEnv.live >>> TestEnvironment.live +!+ layer +!+ TestLogger.fromConsole(Console.ConsoleLive))).catchAllCause(layerError =>
          ZLayer.fail(layerError.prettyPrint) // TODO Discuss this Any => String error mapping.  Probably not good.
        )
      )
      _ <- ZIO.when (summary.status == Summary.Failure)(ZIO.fail(summary.summary))
    } yield summary
  }

  final def <>(that: ZIOSpecAbstract)(implicit trace: ZTraceElement): ZIOSpecAbstract =
    new ZIOSpecAbstract {
      type Environment = self.Environment with that.Environment

      def layer: ZLayer[ZIOAppArgs with Scope, Any, Environment] =
        self.layer +!+ that.layer

      override def runSpec: ZIO[
        Environment with TestEnvironment with ZIOAppArgs with Scope,
        Nothing,
        Summary
      ] =
        self.runSpec.zipPar(that.runSpec).map { case (summary1, summary2) =>
          summary1.add(summary2)
        }

      def spec: Spec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any] =
        self.aspects.foldLeft(self.spec)(_ @@ _) + that.aspects.foldLeft(that.spec)(_ @@ _)

      def tag: EnvironmentTag[Environment] = {
        implicit val selfTag: EnvironmentTag[self.Environment] = self.tag
        implicit val thatTag: EnvironmentTag[that.Environment] = that.tag
        val _                                                  = (selfTag, thatTag)
        EnvironmentTag[Environment]
      }

      override def aspects: Chunk[TestAspectAtLeastR[Environment with TestEnvironment with ZIOAppArgs]] =
        Chunk.empty
    }

  protected def runSpec: ZIO[
    Environment with TestEnvironment with ZIOAppArgs with Scope,
    Nothing,
    Summary
  ] = {
    implicit val trace = ZTraceElement.empty
    for {
      args    <- ZIO.service[ZIOAppArgs]
      console <- ZIO.console
      testArgs = TestArgs.parse(args.getArgs.toArray)
      summary <- runSpecInfallible(spec, testArgs, console)
    } yield summary
  }

  private def createTestReporter(rendererName: String)(implicit trace: ZTraceElement): TestReporter[Any] = {
    val renderer = rendererName match {
      case "intellij" => IntelliJRenderer
      case _          => TestRenderer.default
    }
    testReporter(renderer, TestAnnotationRenderer.default)
  }

  /*
   * Regardless of test assertion or runtime failures, this method will always return a summary
   * capturing this information
   */
  private[zio] def runSpecInfallible(
    spec: Spec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any],
    testArgs: TestArgs,
    console: Console
  )(implicit
    trace: ZTraceElement
  ): URIO[
    TestEnvironment with ZIOAppArgs with Scope,
    Summary
  ] = {
    val filteredSpec = FilteredSpec(spec, testArgs)

    for {
      runtime <-
        ZIO.runtime[
          TestEnvironment with ZIOAppArgs with Scope
        ]
      environment0: ZEnvironment[ZIOAppArgs with Scope] = runtime.environment
      environment1: ZEnvironment[ZIOAppArgs with Scope] = runtime.environment
      runtimeConfig                                     = hook(runtime.runtimeConfig)
      sharedLayer: ZLayer[Any, Any, Environment] =
        ZLayer.succeedEnvironment(environment0) >>> layer
      perTestLayer = (ZLayer.succeedEnvironment(environment1) ++ ZEnv.live) >>> (TestEnvironment.live ++ ZLayer
                       .environment[Scope] ++ ZLayer.environment[ZIOAppArgs])
      executionEventSinkLayer = sinkLayerWithConsole(console)
      runner =
        TestRunner(
          TestExecutor
            .default[
              Environment,
              Any
            ](
              sharedLayer,
              perTestLayer,
              executionEventSinkLayer
            ),
          runtimeConfig
        )
      testReporter = testArgs.testRenderer.fold(runner.reporter)(createTestReporter)
      summary <-
        runner.withReporter(testReporter).run(aspects.foldLeft(filteredSpec)(_ @@ _))
    } yield summary
  }

}
