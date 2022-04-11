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

  def aspects: Chunk[TestAspectAtLeastR[Environment with TestEnvironment with ZIOAppArgs]] =
    Chunk(TestAspect.fibers)

  def testReporter(testRenderer: TestRenderer, testAnnotationRenderer: TestAnnotationRenderer)(implicit
    trace: ZTraceElement
  ): TestReporter[Any] =
    DefaultTestReporter(testRenderer, testAnnotationRenderer)

  final def run: ZIO[ZIOAppArgs with Scope, Any, Summary] = {
    implicit val trace = ZTraceElement.empty

    runSpec.provideSomeLayer[ZIOAppArgs with Scope](
      ZLayer.environment[ZIOAppArgs with Scope] +!+
        (ZEnv.live >>> TestEnvironment.live +!+ layer +!+ TestLogger.fromConsole(Console.ConsoleLive))
    )
  }

  final def <>(that: ZIOSpecAbstract)(implicit trace: ZTraceElement): ZIOSpecAbstract = {
//    println("Composing specs. A: " + this.getClass.getName + " B: " + that.getClass.getName)
    new ZIOSpecAbstract {
      type Environment = self.Environment with that.Environment

      def layer: ZLayer[ZIOAppArgs with Scope, Any, Environment] =
        self.layer +!+ that.layer

      override def runSpec: ZIO[
        Environment with TestEnvironment with ZIOAppArgs with Scope,
        Any,
        Summary
      ] =
        self.runSpec.zipPar(that.runSpec).map { case (summary1, summary2) =>
          summary1
        }

      def spec: ZSpec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any] =
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
  }

  protected def runSpec: ZIO[
    Environment with TestEnvironment with ZIOAppArgs with Scope,
    Any,
    Summary
  ] = {
    implicit val trace = ZTraceElement.empty
    for {
      args    <- ZIO.service[ZIOAppArgs]
      console <- ZIO.console
      testArgs = TestArgs.parse(args.getArgs.toArray)
      summary <- runSpec(spec, testArgs, console)
    } yield summary
  }

  private def createTestReporter(rendererName: String)(implicit trace: ZTraceElement): TestReporter[Any] = {
    val renderer = rendererName match {
      case "intellij" => IntelliJRenderer
      case _          => TestRenderer.default
    }
    testReporter(renderer, TestAnnotationRenderer.default)
  }

  private[zio] def runSpec(
    spec: ZSpec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any],
    testArgs: TestArgs,
    console: Console
  )(implicit
    trace: ZTraceElement
  ): URIO[
    TestEnvironment with ZIOAppArgs with Scope,
    Summary
  ] = {
    val l: ZLayer[ZIOAppArgs with Scope, Any, Environment] = layer
    val filteredSpec = FilteredSpec(spec, testArgs)

    for {
      _ <- ZIO.debug("ZIOSpecAbstract.runSpec")
      runtime <-
        ZIO.runtime[
          TestEnvironment with ZIOAppArgs with Scope
        ]
      environment0: ZEnvironment[ZIOAppArgs with Scope] = runtime.environment
      environment1: ZEnvironment[ZIOAppArgs with Scope] = runtime.environment
      environment: ZEnvironment[TestEnvironment with ZIOAppArgs with Scope with Annotations] = runtime.environment
      runtimeConfig = hook(runtime.runtimeConfig)
      sharedLayer: ZLayer[Any, Any, Environment] =
        ZLayer.succeedEnvironment(environment0) >>> layer
      perTestLayer = (ZLayer.succeedEnvironment(environment1) ++ ZEnv.live) >>> (TestEnvironment.live ++ ZLayer.environment[Scope] ++ ZLayer.environment[ZIOAppArgs])
      runner =
        TestRunner(
          TestExecutor
            .default[
              Environment,
              Any
            ](
              sharedLayer, // shared layer???
              perTestLayer,//ZLayer.succeedEnvironment(environment), // per test layer
              (TestLogger.fromConsole( // execution layer
                console
              ) >>> ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live)
            ),
          runtimeConfig
        )
      testReporter = testArgs.testRenderer.fold(runner.reporter)(createTestReporter)
      summary <-
        runner.withReporter(testReporter).run(aspects.foldLeft(filteredSpec)(_ @@ _))
    } yield summary
  }

}
