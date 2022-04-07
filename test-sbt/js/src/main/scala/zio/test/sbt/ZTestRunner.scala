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
import zio.test.{FilteredSpec, Summary, TestArgs, TestEnvironment, TestLogger, ZIOSpecAbstract}
import zio.{Exit, Layer, Runtime, Scope, ZEnvironment, ZIO, ZIOAppArgs, ZLayer}

import scala.collection.mutable

sealed abstract class ZTestRunner(
  val args: Array[String],
  val remoteArgs: Array[String],
  testClassLoader: ClassLoader,
  runnerType: String
) extends Runner {
  def sendSummary: SendSummary

  val summaries: mutable.Buffer[Summary] = mutable.Buffer.empty

  def done(): String = {
    val total  = summaries.map(_.total).sum
    val ignore = summaries.map(_.ignore).sum

    if (summaries.isEmpty || total == ignore)
      s"${Console.YELLOW}No tests were executed${Console.RESET}"
    else
      summaries.map(_.summary).filter(_.nonEmpty).flatMap(s => colored(s) :: "\n" :: Nil).mkString("", "", "Done")
  }

  def tasks(defs: Array[TaskDef]): Array[Task] = {
    tasksZ(defs).toArray
  }

  def tasksZ(defs: Array[TaskDef]): Option[ZTestTask] = {
    val tasks = defs.map(ZTestTask(_, testClassLoader, runnerType, sendSummary, TestArgs.parse(args)))

    val taskPolicy = new ZTestTaskPolicyDefaultImpl
    taskPolicy.merge(tasks)
  }

  override def receiveMessage(summary: String): Option[String] = {
    SummaryProtocol.deserialize(summary).foreach(s => summaries += s)

    None
  }

  override def serializeTask(task: Task, serializer: TaskDef => String): String =
    serializer(task.taskDef())

  override def deserializeTask(task: String, deserializer: String => TaskDef): Task =
    ZTestTask(deserializer(task), testClassLoader, runnerType, sendSummary, TestArgs.parse(args))
}

final class ZMasterTestRunner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends ZTestRunner(args, remoteArgs, testClassLoader, "master") {

  //This implementation seems to be used when there's only single spec to run
  override val sendSummary: SendSummary = SendSummary.fromSend { summary =>
    summaries += summary
    ()
  }

}

final class ZSlaveTestRunner(
  args: Array[String],
  remoteArgs: Array[String],
  testClassLoader: ClassLoader,
  val sendSummary: SendSummary
) extends ZTestRunner(args, remoteArgs, testClassLoader, "slave") {}

sealed class ZTestTask(
  taskDef: TaskDef,
  testClassLoader: ClassLoader,
  val runnerType: String,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  spec: ZIOSpecAbstract
) extends BaseTestTask(taskDef, testClassLoader, sendSummary, testArgs, spec) {

  def execute(eventHandler: EventHandler, loggers: Array[Logger], continuation: Array[Task] => Unit): Unit = {
    val zioSpec = spec

    val fullLayer: Layer[
      Error,
      zioSpec.Environment with ZIOAppArgs with TestEnvironment with Scope with TestLogger
    ] =
      constructLayer[zioSpec.Environment](zioSpec.layer, zio.Console.ConsoleLive)

    Runtime(ZEnvironment.empty, zioSpec.hook(zioSpec.runtime.runtimeConfig)).unsafeRunAsyncWith {
      val logic =
        for {
          summary <- zioSpec
                       .runSpec(FilteredSpec(zioSpec.spec, args), args, zio.Console.ConsoleLive)
                       .provideLayer(
                         fullLayer
                       )
          _ <- sendSummary.provide(ZLayer.succeed(summary))
          // TODO Confirm if/how these events needs to be handled in #6481
          //    Check XML behavior
          _ <- ZIO.when(summary.fail > 0) {
                 ZIO.fail("Failed tests")
               }
        } yield ()
      logic
    } { exit =>
      exit match {
        case Exit.Failure(_) => Console.err.println(s"$runnerType failed.")
        case _               =>
      }
      continuation(Array())
    }
  }
}
object ZTestTask {
  def apply(
    taskDef: TaskDef,
    testClassLoader: ClassLoader,
    runnerType: String,
    sendSummary: SendSummary,
    args: TestArgs
  ): ZTestTask = {
    val zioSpec = disectTask(taskDef, testClassLoader)
    new ZTestTask(taskDef, testClassLoader, runnerType, sendSummary, args, zioSpec)
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
          println("Merging in new task: " + nextSpec.spec)
          Some(
            new ZTestTask(
              composedTask.taskDef,
              composedTask.testClassLoader,
              composedTask.runnerType,
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
