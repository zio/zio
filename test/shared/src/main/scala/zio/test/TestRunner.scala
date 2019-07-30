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
import zio.console.Console
import zio.internal.{ Platform, PlatformLive }

/**
 * A `TestRunner[R, E, L]` encapsulates all the logic necessary to run specs that
 * require an environment `R` and may fail with an error `E`, using labels of
 * type `L`. Test runners require a test executor, a platform, and a reporter.
 */
abstract class TestRunner[L, T](
  executor: TestExecutor[L, T],
  platform: Platform = PlatformLive.makeDefault().withReportFailure(_ => ()),
  reporter: TestReporter[L] = DefaultTestReporter(Console.Live)
) { self =>

  /**
   * Runs the spec, producing the execution results.
   */
  final def run(spec: Spec[L, T]): UIO[ExecutedSpec[L]] =
    executor.execute(spec, ExecutionStrategy.ParallelN(4)).flatMap { results =>
      reporter.report(results) *> ZIO.succeed(results)
    }

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  final def unsafeRunAsync(spec: Spec[L, T])(k: ExecutedSpec[L] => Unit): Unit =
    Runtime((), platform).unsafeRunAsync(run(spec)) {
      case Exit.Success(v) => k(v)
      case Exit.Failure(c) => throw FiberFailure(c)
    }
}
