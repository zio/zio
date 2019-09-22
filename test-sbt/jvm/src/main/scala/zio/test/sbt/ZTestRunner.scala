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

import java.util.concurrent.atomic.AtomicReference

import sbt.testing._

final class ZTestRunner(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  val createdTasks = new AtomicReference[Array[ZTestTask]](Array.empty)

  def done(): String =
    createdTasks.get
      .map(_.summaryRef.get)
      .filter(_.nonEmpty)
      .flatMap(summary => summary :: "\n" :: Nil)
      .mkString("", "", "Done")

  def tasks(defs: Array[TaskDef]): Array[Task] = {
    val theTasks = defs.map(new ZTestTask(_, testClassLoader))
    createdTasks.updateAndGet(_ ++ theTasks)

    theTasks.asInstanceOf[Array[Task]]
  }
}

class ZTestTask(taskDef: TaskDef, testClassLoader: ClassLoader) extends BaseTestTask(taskDef, testClassLoader)
