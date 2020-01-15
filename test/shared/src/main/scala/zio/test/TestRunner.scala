/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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
import zio.internal.Platform
import zio.scheduler.Scheduler

/**
 * A `TestRunner[R, E, L, T, S]` encapsulates all the logic necessary to run
 * specs that contain tests of type `T`, require an environment `R` and may
 * fail with an error `E` or succeed with an `S`, using labels of type `L`.
 * Test runners require a test executor, a platform, and a reporter.
 */
final case class TestRunner[R, E, L, -T, S](
  executor: TestExecutor[R, E, L, T, S],
  platform: Platform = Platform.makeDefault().withReportFailure(_ => ()),
  reporter: TestReporter[E, L, S] = DefaultTestReporter(TestAnnotationRenderer.default),
  bootstrap: Managed[Nothing, TestLogger with Clock] =
    ((Console.live >>> TestLogger.fromConsole) ++ (Scheduler.live >>> Clock.live)).build
) { self =>

  lazy val runtime = Runtime((), platform)

  /**
   * Runs the spec, producing the execution results.
   */
  def run(spec: ZSpec[R, E, L, T]): URIO[TestLogger with Clock, ExecutedSpec[E, L, S]] =
    executor.run(spec, ExecutionStrategy.ParallelN(4)).timed.flatMap {
      case (duration, results) => reporter(duration, results).as(results)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  def unsafeRun(
    spec: ZSpec[R, E, L, T]
  ): ExecutedSpec[E, L, S] =
    runtime.unsafeRun(run(spec).provideManaged(bootstrap))

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  def unsafeRunAsync(
    spec: ZSpec[R, E, L, T]
  )(
    k: ExecutedSpec[E, L, S] => Unit
  ): Unit =
    runtime.unsafeRunAsync(run(spec).provideManaged(bootstrap)) {
      case Exit.Success(v) => k(v)
      case Exit.Failure(c) => throw FiberFailure(c)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  def unsafeRunSync(
    spec: ZSpec[R, E, L, T]
  ): Exit[Nothing, ExecutedSpec[E, L, S]] =
    runtime.unsafeRunSync(run(spec).provideManaged(bootstrap))

  /**
   * Creates a copy of this runner replacing the reporter.
   */
  def withReporter[E1 >: E, L1 >: L, S1 >: S](reporter: TestReporter[E1, L1, S1]) =
    copy(reporter = reporter)

  /**
   * Creates a copy of this runner replacing the platform
   */
  def withPlatform(f: Platform => Platform): TestRunner[R, E, L, T, S] =
    copy(platform = f(platform))

  private[test] def buildRuntime: Managed[Nothing, Runtime[TestLogger with Clock]] =
    bootstrap.map(Runtime(_, platform))
}
