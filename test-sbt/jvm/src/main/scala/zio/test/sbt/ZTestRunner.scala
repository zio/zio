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

package zio.test.sbt

import sbt.testing._
import zio.ZIO
import zio.test.{Summary, TestArgs, ZIOSpecAbstract}

import java.util.concurrent.atomic.AtomicReference

final class ZTestRunner(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  val summaries: AtomicReference[Vector[Summary]] = new AtomicReference(Vector.empty)

  val sendSummary: SendSummary = SendSummary.fromSendM(summary =>
    ZIO.succeed {
      summaries.updateAndGet(_ :+ summary)
      ()
    }
  )

  def done(): String = {
    val allSummaries = summaries.get

    val total  = allSummaries.map(_.total).sum
    val ignore = allSummaries.map(_.ignore).sum

    if (allSummaries.isEmpty || total == ignore)
      s"${Console.YELLOW}No tests were executed${Console.RESET}"
    else
      allSummaries
        .map(_.summary)
        .filter(_.nonEmpty)
        .flatMap(summary => colored(summary) :: "\n" :: Nil)
        .mkString("", "", "Done")
  }

  def tasks(defs: Array[TaskDef]): Array[Task] =
    tasksZ(defs).toArray

  private[sbt] def tasksZ(defs: Array[TaskDef]): Option[ZTestTask] = {
    val testArgs                = TestArgs.parse(args)
    val tasks: Array[ZTestTask] = defs.map(ZTestTask(_, testClassLoader, sendSummary, testArgs))
    val entrypointClass: String = testArgs.testTaskPolicy.getOrElse(classOf[ZTestTaskPolicyDefaultImpl].getName)
    val taskPolicy = getClass.getClassLoader
      .loadClass(entrypointClass)
      .getConstructor()
      .newInstance()
      .asInstanceOf[ZTestTaskPolicy]
    taskPolicy.merge(tasks)
  }
}

final class ZTestTask(
  taskDef: TaskDef,
  testClassLoader: ClassLoader,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  spec: ZIOSpecAbstract
) extends BaseTestTask(taskDef, testClassLoader, sendSummary, testArgs, spec)

object ZTestTask {
  def apply(
    taskDef: TaskDef,
    testClassLoader: ClassLoader,
    sendSummary: SendSummary,
    args: TestArgs
  ): ZTestTask = {
    val zioSpec = disectTask(taskDef, testClassLoader)
    new ZTestTask(taskDef, testClassLoader, sendSummary, args, zioSpec)
  }

  private def disectTask(taskDef: TaskDef, testClassLoader: ClassLoader): ZIOSpecAbstract = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName().stripSuffix("$") + "$"

    Reflect
      .lookupLoadableModuleClass(fqn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
      .asInstanceOf[ZIOSpecAbstract]
  }
}

abstract class ZTestTaskPolicy {
  def merge(zioTasks: Array[ZTestTask]): Option[ZTestTask]
}

class ZTestTaskPolicyDefaultImpl extends ZTestTaskPolicy {

  override def merge(zioTasks: Array[ZTestTask]): Option[ZTestTask] =
    zioTasks.foldLeft(Option.empty[ZTestTask]) { case (newTests, nextSpec) =>
      newTests match {
        case Some(composedTask) =>
          Some(
            new ZTestTask(
              composedTask.taskDef,
              composedTask.testClassLoader,
              composedTask.sendSummary,
              composedTask.args,
              composedTask.spec <> nextSpec.spec
            )
          )
        case None =>
          Some(nextSpec)
      }
    }

}
