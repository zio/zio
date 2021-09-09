/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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
import zio.test.environment.TestEnvironment
import zio.test.render._

@EnableReflectiveInstantiation
abstract class ZIOSpec[R <: Has[_]: Tag](val layer: ZLayer[TestEnvironment, Any, R]) extends ZIOApp { self =>

  def spec: ZSpec[R with TestEnvironment with Has[ZIOAppArgs], Any]

  def aspects: Chunk[TestAspect[Nothing, R with TestEnvironment with Has[ZIOAppArgs], Nothing, Any]] =
    Chunk.empty

  final def run: ZIO[ZEnv with Has[ZIOAppArgs], Any, Any] =
    runSpec.provideSomeLayer[ZEnv with Has[ZIOAppArgs]](TestEnvironment.live ++ (TestEnvironment.live >>> layer))

  final def <>[R1 <: R: Tag](that: ZIOSpec[R1]): ZIOSpec[R with R1] =
    new ZIOSpec[R with R1](self.layer ++ that.layer) {
      override def runSpec: ZIO[R with R1 with TestEnvironment with Has[ZIOAppArgs], Any, Any] =
        self.runSpec.zipPar(that.runSpec)
      def spec: ZSpec[R with R1 with TestEnvironment with Has[ZIOAppArgs], Any] =
        self.spec + that.spec
    }

  protected def runSpec: ZIO[R with TestEnvironment with Has[ZIOAppArgs], Any, Any] =
    for {
      args     <- ZIO.service[ZIOAppArgs]
      testArgs  = TestArgs.parse(args.args.toArray)
      exitCode <- runSpec(spec, testArgs)
      _        <- doExit(exitCode)
    } yield ()

  private def createTestReporter(rendererName: String): TestReporter[Any] = {
    val renderer = rendererName match {
      case "intellij" => IntelliJRenderer
      case _          => TestRenderer.default
    }
    DefaultTestReporter(renderer, TestAnnotationRenderer.default)
  }

  private def doExit(exitCode: Int): UIO[Unit] =
    exit(ExitCode(exitCode)).when(!isAmmonite).unit

  private def isAmmonite: Boolean =
    sys.env.exists { case (k, v) =>
      k.contains("JAVA_MAIN_CLASS") && v == "ammonite.Main"
    }

  private def runSpec(
    spec: ZSpec[R with TestEnvironment with Has[ZIOAppArgs], Any],
    testArgs: TestArgs
  ): URIO[R with TestEnvironment with Has[ZIOAppArgs], Int] = {
    val filteredSpec = FilteredSpec(spec, testArgs)

    for {
      env <- ZIO.environment[R with TestEnvironment with Has[ZIOAppArgs]]
      runner = TestRunner(
                 TestExecutor.default[R with TestEnvironment with Has[ZIOAppArgs], Any](ZLayer.succeedMany(env))
               )
      testReporter = testArgs.testRenderer.fold(runner.reporter)(createTestReporter)
      results <-
        runner.withReporter(testReporter).run(aspects.foldLeft(filteredSpec)(_ @@ _)).provideLayer(runner.bootstrap)
      hasFailures = results.exists {
                      case ExecutedSpec.TestCase(test, _) => test.isLeft
                      case _                              => false
                    }
      _ <- TestLogger
             .logLine(SummaryBuilder.buildSummary(results).summary)
             .when(testArgs.printSummary)
             .provideLayer(runner.bootstrap)
    } yield if (hasFailures) 1 else 0
  }
}
