/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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
import zio.test._
import zio.{Exit, Runtime, UIO, ZEnv, ZIO}

import scala.collection.mutable
import scala.io.AnsiColor

sealed abstract class ZTestRunner(
  val args: Array[String],
  val remoteArgs: Array[String],
  testClassLoader: ClassLoader,
  runnerType: String
) extends Runner {

  var layerCache: Map[Any, Any] = Map[Any, Any]()
  val runtime: Runtime[ZEnv]    = Fun.withFunExecutor(Runtime.default)

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
    val (specs, tasks) =
      defs.map { taskDef =>
        val spec = lookupSpec(taskDef, testClassLoader)
        spec -> new ZTestTask(
          taskDef,
          runnerType,
          sendSummary,
          TestArgs.parse(args),
          spec,
          layerCache
        )
      }.unzip

    layerCache = runtime.unsafeRun {
      ZIO
        .foreach(specs.map(_.sharedLayer).toSet) { sharedLayer =>
          (ZEnv.live >>> sharedLayer).build.use { sharedEnv =>
            UIO(sharedLayer -> sharedEnv)
          }
        }
        .map(_.toMap)
    }

    tasks.toArray
  }

  override def receiveMessage(summary: String): Option[String] = {
    SummaryProtocol.deserialize(summary).foreach(s => summaries += s)
    None
  }

  override def serializeTask(task: Task, serializer: TaskDef => String): String =
    serializer(task.taskDef())

  override def deserializeTask(task: String, deserializer: String => TaskDef): Task = {
    val taskDef = deserializer(task)
    new ZTestTask(
      taskDef,
      runnerType,
      sendSummary,
      TestArgs.parse(args),
      lookupSpec(taskDef, testClassLoader),
      layerCache
    )
  }
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

final class ZTestTask(
  taskDef: TaskDef,
  runnerType: String,
  sendSummary: SendSummary,
  testArgs: TestArgs,
  specInstance0: AbstractRunnableSpec,
  layerCache: Map[Any, Any]
) extends BaseTestTask(
      taskDef,
      sendSummary,
      testArgs,
      specInstance0
    ) {

  def execute(eventHandler: EventHandler, loggers: Array[Logger], continuation: Array[Task] => Unit): Unit =
    Runtime((), specInstance.platform).unsafeRunAsync {
      if (runnerType == "master") {
        val sharedEnv = layerCache(specInstance.sharedLayer).asInstanceOf[specInstance.SharedEnvironment]
        sbtTestLayer(loggers).build.use { sbtTestLayer =>
          val env = sharedEnv ++ sbtTestLayer
          run(eventHandler).provide(env)
        }
      } else
        UIO(
          loggers.foreach(
            _.warn(
              AnsiColor.YELLOW +
                s"WARNING: could not share layers with ${taskDef.fullyQualifiedName()}${AnsiColor.RESET}\n" +
                "ScalaJS tests are run in several different VM instances, when " +
                "executed in parallel, therefore it's not possible to provide them " +
                "the same shared layer. Make sure to run ScalaJS tests sequentially " +
                "if you want to share layers among them.\n" +
                "You may want to consider adding to your project settings in build.sbt" +
                "the following line:\n" +
                "Test / parallelExecution := false"
            )
          )
        ).unless(specInstance.sharedLayer == DefaultRunnableSpec.none) *> {
          run(eventHandler).provideLayer(
            (zio.ZEnv.live >>> specInstance.sharedLayer) ++ sbtTestLayer(loggers)
          )
        }
    } { exit =>
      exit match {
        case Exit.Failure(cause) => Console.err.println(s"$runnerType failed: " + cause.prettyPrint)
        case _                   =>
      }
      continuation(Array())
    }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = ???
}
