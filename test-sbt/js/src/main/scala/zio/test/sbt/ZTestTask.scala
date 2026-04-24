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
import zio.{Exit, Trace, Unsafe}
import zio.test.{Summary, TestArgs, TestOutput, ZIOSpecAbstract}

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
  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] =
    throw new IllegalStateException("ZTestTask.execute() without continuation unexpectedly invoked on Scala.js")

  override def execute(eventHandler: EventHandler, loggers: Array[Logger], continuation: Array[Task] => Unit): Unit =
    unsafeAPI
      .fork(run(eventHandler)(Trace.empty))(Trace.empty, Unsafe)
      .unsafe
      .addObserver { exit =>
        exit match {
          case Exit.Failure(cause) => Console.err.println(s"$runnerType failed. $cause")
          case _                   =>
        }
        continuation(Array())
      }(Unsafe)
}
