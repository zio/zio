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
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.ReporterEventRenderer.{ConsoleEventRenderer, IntelliJEventRenderer}
import zio.test.render.{ConsoleRenderer, TestRenderer}

@EnableReflectiveInstantiation
abstract class ZIOSpecAbstract extends ZIOApp with ZIOSpecAbstractVersionSpecific {
  self =>

  def spec: Spec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any]

  def aspects: Chunk[TestAspectAtLeastR[Environment with TestEnvironment with ZIOAppArgs]] =
    Chunk(TestAspect.fibers)

  final def run: ZIO[ZIOAppArgs with Scope, Any, Summary] = {
    implicit val trace = Trace.empty

    runSpec.provideSomeLayer[ZIOAppArgs with Scope](
      ZLayer.environment[ZIOAppArgs with Scope] +!+
        (liveEnvironment >>> TestEnvironment.live +!+ bootstrap +!+ TestLogger.fromConsole(Console.ConsoleLive))
    )
  }

  final def <>(that: ZIOSpecAbstract)(implicit trace: Trace): ZIOSpecAbstract =
    new ZIOSpecAbstract {
      type Environment = self.Environment with that.Environment

      def bootstrap: ZLayer[ZIOAppArgs with Scope, Any, Environment] =
        self.bootstrap +!+ that.bootstrap

      def spec: Spec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any] =
        self.aspects.foldLeft(self.spec)(_ @@ _) + that.aspects.foldLeft(that.spec)(_ @@ _)

      def environmentTag: EnvironmentTag[Environment] = {
        implicit val selfTag: EnvironmentTag[self.Environment] = self.environmentTag
        implicit val thatTag: EnvironmentTag[that.Environment] = that.environmentTag
        val _                                                  = (selfTag, thatTag)
        EnvironmentTag[Environment]
      }

      override def aspects: Chunk[TestAspectAtLeastR[Environment with TestEnvironment with ZIOAppArgs]] =
        Chunk.empty
    }

  protected final def runSpec(implicit trace: Trace): ZIO[
    Environment with TestEnvironment with ZIOAppArgs with Scope,
    Throwable,
    Summary
  ] =
    for {
      args    <- ZIO.service[ZIOAppArgs]
      console <- ZIO.console
      testArgs = TestArgs.parse(args.getArgs.toArray)
      summary <- runSpecInfallible(spec, testArgs, console)
      _ <- ZIO.when(testArgs.printSummary) {
             console.printLine(ConsoleRenderer.renderSummary(summary)).orDie
           }
    } yield summary

  private def getTestEventRenderer(testArgs: TestArgs) =
    testArgs.testRenderer match {
      case Some("intellij") => IntelliJEventRenderer
      case _                => ConsoleEventRenderer
    }

  /*
   * Regardless of test assertion or runtime failures, this method will always return a summary
   * capturing this information
   */
  private[zio] def runSpecInfallible(
    spec: Spec[Environment with TestEnvironment with ZIOAppArgs with Scope, Any],
    testArgs: TestArgs,
    console: Console,
    runtime: Option[Runtime[_]] = None,
    testEventHandler: ZTestEventHandler = ZTestEventHandler.silent
  )(implicit
    trace: Trace
  ): URIO[TestEnvironment with ZIOAppArgs with Scope, Summary] = {
    val filteredSpec = FilteredSpec(spec, testArgs)

    for {
      runtime <- ZIO.fromOption(runtime.collect { case rt: Runtime[Environment with ZIOAppArgs with Scope] =>
                   rt
                 }) orElse ZIO.runtime[TestEnvironment with ZIOAppArgs with Scope]
      environment0: ZEnvironment[ZIOAppArgs with Scope] = runtime.environment
      environment1: ZEnvironment[ZIOAppArgs with Scope] = runtime.environment
      sharedLayer: ZLayer[Any, Any, Environment] =
        ZLayer.succeedEnvironment(environment0) >>> bootstrap
      perTestLayer = (ZLayer.succeedEnvironment(environment1) ++ liveEnvironment) >>> (TestEnvironment.live ++ ZLayer
                       .environment[Scope] ++ ZLayer.environment[ZIOAppArgs])
      eventRenderer           = getTestEventRenderer(testArgs)
      executionEventSinkLayer = sinkLayer(console, eventRenderer)
      runner =
        TestRunner(
          TestExecutor
            .default[Environment, Any](
              sharedLayer,
              perTestLayer,
              executionEventSinkLayer,
              testEventHandler
            )
        )
      summary <- runner.run(aspects.foldLeft(filteredSpec)(_ @@ _) @@ TestAspect.fibers)
    } yield summary
  }
}
