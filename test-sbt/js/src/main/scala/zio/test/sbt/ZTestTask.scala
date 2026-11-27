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

/**
 * The sbt task that runs one ZIO Test spec, created by [[TestRunner]]. It runs the spec on the shared runtime of its runner when there is one, and on `Runtime.default` otherwise. Failed tests are reported to sbt as events; they don't make the task throw.
 *
 * On the JVM and Scala Native, `execute` blocks until the spec finishes and returns no further tasks. When running the spec fails (for example, on Scala Native, when its `bootstrap` layer fails), `execute` logs the full stack trace to each sbt logger and rethrows the error.
 *
 * On Scala.js, the `execute` overload with a continuation starts the spec on a new fiber and returns at once. When the fiber ends, it prints the cause to the standard error stream if the fiber failed, then calls the continuation with no further tasks. The overload without a continuation throws a `java.lang.IllegalStateException`.
 */
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
