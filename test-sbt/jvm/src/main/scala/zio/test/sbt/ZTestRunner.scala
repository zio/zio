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
import zio.test.{AbstractRunnableSpec, Summary, TestArgs, ZIOSpec, ZIOSpecAbstract, sbt}

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

  def tasks(defs: Array[TaskDef]): Array[Task] = {
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

sealed class ZTestTask(
  taskDef: TaskDef,
  testClassLoader: ClassLoader,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  spec: NewOrLegacySpec
) extends BaseTestTask(taskDef, testClassLoader, sendSummary, testArgs, spec)

final class ZTestTaskLegacy(
  taskDef: TaskDef,
  testClassLoader: ClassLoader,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  spec: AbstractRunnableSpec
) extends ZTestTask(taskDef, testClassLoader, sendSummary, testArgs, sbt.LegacySpecWrapper(spec))

final class ZTestTaskNew(
  taskDef: TaskDef,
  testClassLoader: ClassLoader,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  val newSpec: ZIOSpecAbstract
) extends ZTestTask(taskDef, testClassLoader, sendSummary, testArgs, sbt.NewSpecWrapper(newSpec))

object ZTestTask {
  def apply(
    taskDef: TaskDef,
    testClassLoader: ClassLoader,
    sendSummary: SendSummary,
    args: TestArgs
  ): ZTestTask =
    disectTask(taskDef, testClassLoader) match {
      case NewSpecWrapper(zioSpec) =>
        new ZTestTaskNew(taskDef, testClassLoader, sendSummary, args, zioSpec)
      case LegacySpecWrapper(abstractRunnableSpec) =>
        new ZTestTaskLegacy(taskDef, testClassLoader, sendSummary, args, abstractRunnableSpec)
    }

  def disectTask(taskDef: TaskDef, testClassLoader: ClassLoader): NewOrLegacySpec = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName().stripSuffix("$") + "$"

    try {
      val res = Reflect
        .lookupLoadableModuleClass(fqn, testClassLoader)
        .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
        .loadModule()
        .asInstanceOf[ZIOSpec[_]]
      sbt.NewSpecWrapper(res)
    } catch {
      case _: ClassCastException =>
        sbt.LegacySpecWrapper(
          Reflect
            .lookupLoadableModuleClass(fqn, testClassLoader)
            .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
            .loadModule()
            .asInstanceOf[AbstractRunnableSpec]
        )
    }
  }
}

abstract class ZTestTaskPolicy {
  def merge(zioTasks: Array[ZTestTask]): Array[Task]
}

case class MergedSpec(spec: ZTestTaskNew, merges: Int)

class ZTestTaskPolicyDefaultImpl extends ZTestTaskPolicy {

  override def merge(zioTasks: Array[ZTestTask]): Array[Task] = {
    val (newTaskOpt, legacyTaskList) =
      zioTasks.foldLeft((List.empty: List[MergedSpec], List[ZTestTaskLegacy]())) {
        case ((newTests, legacyTests), nextSpec) =>
          nextSpec match {
            case legacy: ZTestTaskLegacy => (newTests, legacyTests :+ legacy)
            case taskNew: ZTestTaskNew =>
              newTests match {
                case existingNewTestTask :: otherTasks =>
                  if (existingNewTestTask.merges < 1) {
                    (
                      MergedSpec(
                        new ZTestTaskNew(
                          existingNewTestTask.spec.taskDef,
                          existingNewTestTask.spec.testClassLoader,
                          existingNewTestTask.spec.sendSummary zip taskNew.sendSummary,
                          existingNewTestTask.spec.args,
                          existingNewTestTask.spec.newSpec <> taskNew.newSpec
                        ),
                        existingNewTestTask.merges + 1
                      ) :: otherTasks,
                      legacyTests
                    )
                  } else {
                    (
                      MergedSpec(
                        taskNew,
                        1
                      ) :: existingNewTestTask :: otherTasks,
                      legacyTests
                    )
                  }

                case _ =>
                  (List(MergedSpec(taskNew, 1)), legacyTests)
              }
            case other =>
              throw new RuntimeException("Other case: " + other)
          }
      }

    (legacyTaskList ++ newTaskOpt.map(_.spec)).toArray
  }

}
