/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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
import zio.clock.Clock
import zio.console.Console
import zio.internal.{ Platform, PlatformLive }

/**
 * A `TestRunner[R, E, L, S]` encapsulates all the logic necessary to run specs
 * that require an environment `R` and may fail with an error `E` or succeed
 * with an `S`, using labels of type `L`. Test runners require a test executor,
 * a platform, and a reporter.
 */
case class TestRunner[R, L, -T, E, S](
  executor: TestExecutor[R, L, T, E, S],
  platform: Platform = PlatformLive.makeDefault().withReportFailure(_ => ()),
  reporter: TestReporter[L, E, S] = DefaultTestReporter()
) { self =>

  final val defaultTestLogger: TestLogger = TestLogger.fromConsole(Console.Live)

  /**
   * Runs the spec, producing the execution results.
   */
  final def run(spec: ZSpec[R, E, L, T]): URIO[TestLogger with Clock, ExecutedSpec[L, E, S]] =
    executor(spec, ExecutionStrategy.ParallelN(4)).timed.flatMap {
      case (duration, results) => reporter(duration, results).as(results)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  final def unsafeRun(
    spec: ZSpec[R, E, L, T],
    testLogger: TestLogger = defaultTestLogger,
    clock: Clock = Clock.Live
  ): ExecutedSpec[L, E, S] =
    buildRuntime(testLogger, clock).unsafeRun(run(spec))

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  final def unsafeRunAsync(
    spec: ZSpec[R, E, L, T],
    testLogger: TestLogger = defaultTestLogger,
    clock: Clock = Clock.Live
  )(
    k: ExecutedSpec[L, E, S] => Unit
  ): Unit =
    buildRuntime(testLogger, clock).unsafeRunAsync(run(spec)) {
      case Exit.Success(v) => k(v)
      case Exit.Failure(c) => throw FiberFailure(c)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  final def unsafeRunSync(
    spec: ZSpec[R, E, L, T],
    testLogger: TestLogger = defaultTestLogger,
    clock: Clock = Clock.Live
  ): Exit[Nothing, ExecutedSpec[L, E, S]] =
    buildRuntime(testLogger, clock).unsafeRunSync(run(spec))

  /**
   * Creates a copy of this runner replacing the reporter.
   */
  final def withReporter[L1 >: L, E1 >: E, S1 >: S](reporter: TestReporter[L1, E1, S1]) =
    copy(reporter = reporter)

  /**
   * Creates a copy of this runner replacing the platform
   */
  final def withPlatform(f: Platform => Platform): TestRunner[R, L, T, E, S] =
    copy(platform = f(platform))

  private[test] def buildRuntime(
    loggerSvc: TestLogger = defaultTestLogger,
    clockSvc: Clock = Clock.Live
  ): Runtime[TestLogger with Clock] =
    Runtime(buildEnv(loggerSvc, clockSvc), platform)

  private def buildEnv(loggerSvc: TestLogger, clockSvc: Clock): TestLogger with Clock =
    new TestLogger with Clock {
      override def testLogger: TestLogger.Service = loggerSvc.testLogger
      override val clock: Clock.Service[Any]      = clockSvc.clock
    }
}
