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

package zio.test.sbt

import sbt.testing._
import zio.{ Exit, Runtime }

final class ZTestRunner(
  val args: Array[String],
  val remoteArgs: Array[String],
  testClassLoader: ClassLoader,
  runnerType: String
) extends Runner {
  def done(): String = "Done"
  def tasks(defs: Array[TaskDef]): Array[Task] =
    defs.map(new ZTestTask(_, testClassLoader, runnerType))

  override def receiveMessage(msg: String): Option[String] = None

  override def serializeTask(task: Task, serializer: TaskDef => String): String =
    serializer(task.taskDef)

  override def deserializeTask(task: String, deserializer: String => TaskDef): Task =
    new ZTestTask(deserializer(task), testClassLoader, runnerType)
}

class ZTestTask(taskDef: TaskDef, testClassLoader: ClassLoader, runnerType: String)
    extends BaseTestTask(taskDef, testClassLoader) {

  def execute(eventHandler: EventHandler, loggers: Array[Logger], continuation: Array[Task] => Unit): Unit =
    Runtime((), spec.platform).unsafeRunAsync(run(eventHandler, loggers)) { exit =>
      exit match {
        case Exit.Failure(cause) => Console.err.println(s"$runnerType failed: " + cause.prettyPrint)
        case _                   =>
      }
      continuation(Array())
    }
}
