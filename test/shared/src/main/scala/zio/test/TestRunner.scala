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
 * A `TestRunner[R, E, L]` encapsulates all the logic necessary to run specs that
 * require an environment `R` and may fail with an error `E`, using labels of
 * type `L`. Test runners require a test executor, a platform, and a reporter.
 */
case class TestRunner[L, -T](
  executor: TestExecutor[L, T],
  platform: Platform = PlatformLive.makeDefault().withReportFailure(_ => ()),
  reporter: TestReporter[L] = DefaultTestReporter()
) { self =>

  final val defaultTestLogger: TestLogger = TestLogger.fromConsole(Console.Live)

  /**
   * Runs the spec, producing the execution results.
   */
  final def run(spec: Spec[L, T]): URIO[TestLogger with Clock, ExecutedSpec[L]] =
    executor(spec, ExecutionStrategy.ParallelN(4)).timed.flatMap {
      case (duration, results) => reporter(duration, results).as(results)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  final def unsafeRun(
    spec: Spec[L, T],
    testLogger: TestLogger = defaultTestLogger,
    clock: Clock = Clock.Live
  ): ExecutedSpec[L] =
    Runtime((), platform).unsafeRun(run(spec).provide(buildEnv(testLogger, clock)))

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  final def unsafeRunAsync(spec: Spec[L, T], testLogger: TestLogger = defaultTestLogger, clock: Clock = Clock.Live)(
    k: ExecutedSpec[L] => Unit
  ): Unit =
    Runtime((), platform).unsafeRunAsync(run(spec).provide(buildEnv(testLogger, clock))) {
      case Exit.Success(v) => k(v)
      case Exit.Failure(c) => throw FiberFailure(c)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  final def unsafeRunSync(
    spec: Spec[L, T],
    testLogger: TestLogger = defaultTestLogger,
    clock: Clock = Clock.Live
  ): Exit[Nothing, ExecutedSpec[L]] =
    Runtime((), platform).unsafeRunSync(run(spec).provide(buildEnv(testLogger, clock)))

  /**
   * Creates a copy of this runner replacing the reporter.
   */
  final def withReporter(reporter: TestReporter[L]) = copy(reporter = reporter)

  private def buildEnv(loggerSvc: TestLogger, clockSvc: Clock): TestLogger with Clock = new TestLogger with Clock {
    override def testLogger: TestLogger.Service = loggerSvc.testLogger
    override val clock: Clock.Service[Any]      = clockSvc.clock
  }
}
