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

package zio.test.runner

import com.github.ghik.silencer.silent
import sbt.testing._
import zio.internal.{ Platform, PlatformLive }
import zio.{ RIO, Runtime, ZIO }
import zio.test._
import zio.test.runner.ExecutedSpecStructure.Stats

final class ZTestRunner(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  def done(): String = "Done"
  def tasks(defs: Array[TaskDef]): Array[Task] =
    defs.map(new ZTestTask(_, testClassLoader))
}

class ZTestTask(val taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {

  private val platform: Platform = PlatformLive.makeDefault().withReportFailure(_ => ())

  override def tags(): Array[String] = Array.empty

  private def loadSpec[R, L]: RunnableSpec[R, L] = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName.stripSuffix("$") + "$"
    Reflect
      .lookupLoadableModuleClass(fqn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
      .asInstanceOf[RunnableSpec[R, L]]
  }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {

    val spec = loadSpec[Any, Any]
    val handle = for {
      result <- spec.run
      _ <- ExecutedSpecStructure
            .from(result)
            .traverse(
              handleSuite = handleSuite(eventHandler, loggers),
              handleTestResult = handleTest(eventHandler, loggers)
            )
    } yield ()

    Runtime((), platform).unsafeRunSync(handle)
    Array.empty
  }

  @silent
  private def handleSuite[R](
    eventHandler: EventHandler,
    loggers: Array[Logger]
  )(label: String, stats: Stats): RIO[R, Unit] =
    effects(loggers)(
      _.info(s"SUITE: $label, passed: ${stats.passed}, failed: ${stats.failed}, ignored: ${stats.ignored}")
    )

  private def handleTest[R](
    eventHandler: EventHandler,
    loggers: Array[Logger]
  )(label: String, result: ZTestResult): RIO[R, Unit] =
    for {
      _ <- effects(loggers)(_.info(s"TEST: $label: ${result.rendered}"))
      _ <- ZIO.effect(eventHandler.handle(ZTestEvent.fromSpec(label, result, taskDef.fullyQualifiedName)))
    } yield ()

  private def effects(loggers: Array[Logger])(effect: Logger => Unit): RIO[Any, Unit] =
    ZIO
      .sequence[Any, Throwable, Unit](loggers.map { l =>
        ZIO.effect(effect(l))
      })
      .unit
}

case class ZTestEvent(
  fullyQualifiedName: String,
  selector: Selector,
  status: Status,
  maybeThrowable: Option[Throwable],
  duration: Long
) extends Event {
  def throwable: OptionalThrowable = maybeThrowable.fold(new OptionalThrowable())(new OptionalThrowable(_))
  def fingerprint: Fingerprint     = RunnableSpecFingerprint
}

object ZTestEvent {
  def fromSpec(label: String, testResult: ZTestResult, fullyQualifiedName: String): Event =
    ZTestEvent(
      fullyQualifiedName,
      new TestSelector(label),
      toStatus(testResult),
      maybeThrowable(testResult),
      0L
    )

  private def maybeThrowable(testResult: ZTestResult) = testResult match {
    case ZTestResult.Failure(FailureDetails.Runtime(cause), _) =>
      Some(cause.squashWith {
        case t: Throwable => t
        case other        => new IllegalStateException(s"cause is not throwable, but: ${other.getClass.getName}")
      })
    case _ => None
  }

  private def toStatus(testResult: ZTestResult) = testResult match {
    case _: ZTestResult.Failure => Status.Failure
    case ZTestResult.Success    => Status.Success
    case ZTestResult.Ignored    => Status.Ignored
  }
}
