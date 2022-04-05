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
import zio.test.{Summary, TestArgs, ZIOSpec, ZIOSpecAbstract, sbt}
import zio.{Exit, Runtime, Scope, ZEnvironment, ZIO, ZIOAppArgs, ZLayer}

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

  def tasks(defs: Array[TaskDef]): Array[Task] =
    defs.map(ZTestTask(_, testClassLoader, runnerType, sendSummary, TestArgs.parse(args)))

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
  runnerType: String,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  spec: NewOrLegacySpec
) extends BaseTestTask(taskDef, testClassLoader, sendSummary, testArgs, spec) {

  def execute(eventHandler: EventHandler, continuation: Array[Task] => Unit): Unit =
    spec match {
      case NewSpecWrapper(zioSpec) =>
        Runtime(ZEnvironment.empty, zioSpec.runtime.runtimeConfig).unsafeRunAsyncWith {
          ZIO.scoped {
            zioSpec.run
              .provideLayer(ZIOAppArgs.empty ++ ZLayer.environment[Scope])
              .onError(e => ZIO.succeed(println(e.prettyPrint)))
          }
        } { exit =>
          exit match {
            case Exit.Failure(cause) => Console.err.println(s"$runnerType failed: " + cause.prettyPrint)
            case _                   =>
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
  ): ZTestTask =
    disectTask(taskDef, testClassLoader) match {
      case NewSpecWrapper(zioSpec) =>
        new ZTestTaskNew(taskDef, testClassLoader, runnerType, sendSummary, args, zioSpec)
    }

  def disectTask(taskDef: TaskDef, testClassLoader: ClassLoader): NewOrLegacySpec = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName().stripSuffix("$") + "$"
    // Creating the class from magic ether
    val res = Reflect
      .lookupLoadableModuleClass(fqn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
      .asInstanceOf[ZIOSpec[_]]
    sbt.NewSpecWrapper(res)
  }
}

final class ZTestTaskNew(
  taskDef: TaskDef,
  testClassLoader: ClassLoader,
  runnerType: String,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  val newSpec: ZIOSpecAbstract
) extends ZTestTask(taskDef, testClassLoader, runnerType, sendSummary, testArgs, sbt.NewSpecWrapper(newSpec))
