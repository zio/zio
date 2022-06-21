/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import zio.Clock.ClockLive
import zio._
import zio.test.ReporterEventRenderer.ConsoleEventRenderer

import java.util.concurrent.TimeUnit

/**
 * A `TestRunner[R, E]` encapsulates all the logic necessary to run specs that
 * require an environment `R` and may fail with an error `E`. Test runners
 * require a test executor, a runtime configuration, and a reporter.
 */
final case class TestRunner[R, E](
  executor: TestExecutor[R, E]
) { self =>

  /**
   * Runs the spec, producing the execution results.
   */
  def run(spec: Spec[R, E], defExec: ExecutionStrategy = ExecutionStrategy.ParallelN(4))(implicit
    trace: Trace
  ): UIO[Summary] =
    for {
      start    <- ClockLive.currentTime(TimeUnit.MILLISECONDS)
      summary  <- executor.run(spec, defExec)
      finished <- ClockLive.currentTime(TimeUnit.MILLISECONDS)
      duration  = Duration.fromMillis(finished - start)
    } yield summary.copy(duration = duration)
}
