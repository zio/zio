/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio.{CancelableFuture, Trace, Unsafe}
import zio.test.{Summary, TestArgs, TestOutput, ZIOSpecAbstract}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

final class ZTestTask(
  taskDef: TaskDef,
  spec: ZIOSpecAbstract,
  runnerType: String,
  sendSummary: Summary => Unit,
  testArgs: TestArgs,
  sharedRuntime: Option[zio.Runtime.Scoped[TestOutput]]
) extends TestTask(
      taskDef,
      spec,
      sendSummary,
      testArgs,
      sharedRuntime
    ) {
  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    var resOutter: CancelableFuture[Unit] = null
    try {
      resOutter = unsafeAPI.runToFuture(run(eventHandler)(Trace.empty))(Trace.empty, Unsafe)
      Await.result(resOutter, Duration.Inf)
    } catch {
      case throwable: Throwable =>
        // We need to log the error because the test framework doesn't report the full stack trace
        val msg = renderError(throwable)
        loggers.foreach(_.error(msg))

        if (resOutter != null) resOutter.cancel()
        throw throwable
    }
    Array()
  }

  private def renderError(t: Throwable): String = {
    val sw = new java.io.StringWriter
    sw.write("Encountered an unexpected error during test invocation. Full stack trace:\n\n")
    t.printStackTrace(new java.io.PrintWriter(sw))
    sw.write("\n")
    sw.toString
  }
}
