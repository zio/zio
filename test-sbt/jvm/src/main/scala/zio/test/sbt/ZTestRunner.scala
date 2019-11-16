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
import zio.ZIO
import zio.test.TestArgs

final class ZTestRunner(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  val summaries: AtomicReference[Vector[String]] = new AtomicReference(Vector.empty)

  val sendSummary: SendSummary = SendSummary.fromSendM(
    summary =>
      ZIO.effectTotal {
        summaries.updateAndGet(_ :+ summary)
        ()
      }
  )

  def done(): String =
    if (summaries.get.isEmpty)
      s"${Console.YELLOW}No tests were executed${Console.RESET}"
    else
      summaries.get
        .filter(_.nonEmpty)
        .flatMap(summary => summary :: "\n" :: Nil)
        .mkString("", "", "Done")

  def tasks(defs: Array[TaskDef]): Array[Task] =
    defs.map(new ZTestTask(_, testClassLoader, sendSummary, TestArgs.parse(args)))
}

class ZTestTask(taskDef: TaskDef, testClassLoader: ClassLoader, sendSummary: SendSummary, testArgs: TestArgs)
    extends BaseTestTask(taskDef, testClassLoader, sendSummary, testArgs)
