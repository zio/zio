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
import zio.{Scope, ZIO, ZIOAppArgs, ZLayer, ZTraceElement}
import zio.test.{ExecutionEventSink, Summary, TestArgs, ZIOSpecAbstract, sinkLayerWithConsole, testClock}

import java.util.concurrent.atomic.AtomicReference
import zio.stacktracer.TracingImplicits.disableAutoTrace

final class ZTestRunnerJVM(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  val summaries: AtomicReference[Vector[Summary]] = new AtomicReference(Vector.empty)

  def sendSummary(implicit trace: ZTraceElement): SendSummary = SendSummary.fromSendM(summary =>
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
    tasksZ(defs)(ZTraceElement.empty).toArray

  private[sbt] def tasksZ(
    defs: Array[TaskDef]
  )(implicit trace: ZTraceElement): Array[ZTestTask[ZIOSpecAbstract#Environment with ExecutionEventSink]] = {
    val testArgs = TestArgs.parse(args)

    val specTasks: Array[ZIOSpecAbstract] = defs.map(disectTask(_, testClassLoader))
    val sharedLayer: ZLayer[Any, Any, ZIOSpecAbstract#Environment with ExecutionEventSink] =
      (Scope.default ++ ZIOAppArgs.empty) >>>
        specTasks.map(_.layer).reduce(_ +!+ _) ++ sinkLayerWithConsole(zio.Console.ConsoleLive)

    val runtime: zio.Runtime[zio.test.ZIOSpecAbstract#Environment with ExecutionEventSink] =
      zio.Runtime.unsafeFromLayer(sharedLayer)

    val tasks: Array[ZTestTask[zio.test.ZIOSpecAbstract#Environment with ExecutionEventSink]] =
      defs.map(ZTestTask(_, testClassLoader, sendSummary, testArgs, runtime))
//    val entrypointClass: String = testArgs.testTaskPolicy.getOrElse(classOf[ZTestTaskPolicyDefaultImpl].getName)
//    val taskPolicy = getClass.getClassLoader
//      .loadClass(entrypointClass)
//      .getConstructor()
//      .newInstance()
//      .asInstanceOf[ZTestTaskPolicy]
//    taskPolicy.merge(tasks)
    tasks
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

final class ZTestTask[T](
  taskDef: TaskDef,
  testClassLoader: ClassLoader,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  spec: ZIOSpecAbstract,
  runtime: zio.Runtime[T]
) extends BaseTestTask(taskDef, testClassLoader, sendSummary, testArgs, spec, runtime)

object ZTestTask {
  def apply[T](
    taskDef: TaskDef,
    testClassLoader: ClassLoader,
    sendSummary: SendSummary,
    args: TestArgs,
    runtime: zio.Runtime[T]
  ): ZTestTask[T] = {
    val zioSpec = disectTask(taskDef, testClassLoader)
    new ZTestTask(taskDef, testClassLoader, sendSummary, args, zioSpec, runtime)
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

//abstract class ZTestTaskPolicy {
//  def merge(zioTasks: Array[ZTestTask[_]])(implicit trace: ZTraceElement): Option[ZTestTask[ZIOSpecAbstract#Environment]]
//}

//class ZTestTaskPolicyDefaultImpl extends ZTestTaskPolicy {
//
//  override def merge(zioTasks: Array[ZTestTask[_]])(implicit trace: ZTraceElement): Option[ZTestTask[ZIOSpecAbstract#Environment]] = {
//    val res =
//      zioTasks.foldLeft(Option.empty[ZTestTask[ZIOSpecAbstract#Environment]]) { case (newTests, nextSpec) =>
//        newTests match {
//          case Some(composedTask) =>
//            Some(
//              new ZTestTask[ZIOSpecAbstract#Environment](
//                composedTask.taskDef,
//                composedTask.testClassLoader,
//                composedTask.sendSummary,
//                composedTask.args,
//                composedTask.spec <> nextSpec.spec,
//                ???
//              )
//            )
//          case None =>
//            Some(nextSpec)
//        }
//      }
//
//    val scope = Scope.default
//    val appArgs = ZIOAppArgs.empty
//    val runtime: zio.Runtime[zio.test.ZIOSpecAbstract#Environment] = zio.Runtime.unsafeFromLayer( (Scope.default ++ ZIOAppArgs.empty) >>> res.get.spec.layer)
//    res
//  }
//
//}
