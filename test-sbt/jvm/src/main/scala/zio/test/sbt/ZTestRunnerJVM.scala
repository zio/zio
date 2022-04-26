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
import zio.test.{ExecutionEventSink, Summary, TestArgs, ZIOSpecAbstract, sinkLayer}

import java.util.concurrent.atomic.AtomicReference
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.ConsoleRenderer

final class ZTestRunnerJVM(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  val summaries: AtomicReference[Vector[Summary]] = new AtomicReference(Vector.empty)

  def sendSummary(implicit trace: ZTraceElement): SendSummary = SendSummary.fromSendZIO(summary =>
    ZIO.succeed {
      summaries.updateAndGet(_ :+ summary)
      ()
    }
  )

  def done(): String = {
    val allSummaries = summaries.get

    val total  = allSummaries.map(_.total).sum
    val ignore = allSummaries.map(_.ignore).sum

    val compositeSummary =
      allSummaries.foldLeft(Summary(0, 0, 0, ""))(_.add(_))

    val renderedSummary = ConsoleRenderer.render(compositeSummary)

    if (allSummaries.isEmpty || total == ignore)
      s"${Console.YELLOW}No tests were executed${Console.RESET}"
    else {
      renderedSummary +
        allSummaries
          .map(_.summary)
          .filter(_.nonEmpty)
          .flatMap(summary => colored(summary) :: "\n" :: Nil)
          .mkString("", "", "Done")
    }
  }

  def tasks(defs: Array[TaskDef]): Array[Task] =
    tasksZ(defs)(ZTraceElement.empty).toArray

  private[sbt] def tasksZ(
    defs: Array[TaskDef]
  )(implicit trace: ZTraceElement): Array[ZTestTask[ExecutionEventSink]] = {
    val testArgs = TestArgs.parse(args)

    val specTasks: Array[ZIOSpecAbstract] = defs.map(disectTask(_, testClassLoader))
    val sharedLayerFromSpecs: ZLayer[Any, Any, Any] =
      (Scope.default ++ ZIOAppArgs.empty) >>> specTasks.map(_.layer).reduce(_ +!+ _)

    val sharedLayer: ZLayer[Any, Any, ExecutionEventSink] =
      sharedLayerFromSpecs +!+ sinkLayer

    val runtime: zio.Runtime[ExecutionEventSink] =
      zio.Runtime.unsafeFromLayer(sharedLayer)

    defs.map(ZTestTask(_, testClassLoader, sendSummary, testArgs, runtime))
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
