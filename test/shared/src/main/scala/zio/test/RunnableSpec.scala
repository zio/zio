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

import zio.clock.Clock
import zio.{ Has, URIO }

/**
 * A `RunnableSpec` has a main function and can be run by the JVM / Scala.js.
 */
trait RunnableSpec[R <: Has[_], E] extends AbstractRunnableSpec {
  override type Environment = R
  override type Failure     = E

  private def run(spec: ZSpec[Environment, Failure]): URIO[TestLogger with Clock, Int] =
    for {
      executedSpec <- runSpec(spec)
      summary      = SummaryBuilder.buildSummary(executedSpec)
      _            <- TestLogger.logLine(summary.summary)
    } yield if (executedSpec.hasFailures) 1 else 0

  /**
   * A simple main function that can be used to run the spec.
   */
  final def main(args: Array[String]): Unit = {
    val testArgs     = TestArgs.parse(args)
    val filteredSpec = FilteredSpec(spec, testArgs)
    val runtime      = runner.runtime
    if (TestPlatform.isJVM) {
      val exitCode = runtime.unsafeRun(run(filteredSpec).provideLayer(runner.bootstrap))
      doExit(exitCode)
    } else if (TestPlatform.isJS) {
      runtime.unsafeRunAsync[Nothing, Int](run(filteredSpec).provideLayer(runner.bootstrap)) { exit =>
        val exitCode = exit.getOrElse(_ => 1)
        doExit(exitCode)
      }
    }
  }

  private def doExit(exitCode: Int): Unit =
    try if (!isAmmonite) sys.exit(exitCode)
    catch { case _: SecurityException => }

  private def isAmmonite: Boolean =
    sys.env.exists {
      case (k, v) => k.contains("JAVA_MAIN_CLASS") && v == "ammonite.Main"
    }
}
