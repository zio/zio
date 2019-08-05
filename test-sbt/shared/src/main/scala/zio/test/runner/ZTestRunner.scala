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

import sbt.testing._
import zio.test.RenderedResult.CaseType
import zio.test._
import zio.{ Runtime, ZIO }

final class ZTestRunner(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  def done(): String = "Done"
  def tasks(defs: Array[TaskDef]): Array[Task] =
    defs.map(new ZTestTask(_, testClassLoader))
}

class ZTestTask(val taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {
  override def tags(): Array[String] = Array.empty

  private def loadSpec[R, L]: RunnableSpec = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName.stripSuffix("$") + "$"
    Reflect
      .lookupLoadableModuleClass(fqn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
      .asInstanceOf[RunnableSpec]
  }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val spec = loadSpec[Any, Any]

    def logResult[R, E](result: RenderedResult): zio.Task[Unit] =
      ZIO
        .sequence[Any, Throwable, Unit](
          for {
            log  <- loggers.toSeq
            line <- result.rendered
          } yield ZIO.effect(log.info(line))
        )
        .unit

    def reportResults(results: Seq[RenderedResult]) =
      ZIO.foreach(results) { result =>
        logResult(result) *> {
          result.caseType match {
            case CaseType.Test =>
              ZIO.effect(
                eventHandler.handle(ZTestEvent.from(result, taskDef.fullyQualifiedName))
              )
            case CaseType.Suite => ZIO.unit
          }
        }
      }

    val handle = for {
      result   <- spec.run
      rendered = DefaultTestReporter.render(result.mapLabel(_.toString))
      _        <- reportResults(rendered)
    } yield ()

    Runtime((), spec.platform).unsafeRunSync(handle)
    Array.empty
  }

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
  def fromSpec(label: String, testResult: TestResult, fullyQualifiedName: String): Event =
    ZTestEvent(
      fullyQualifiedName,
      new TestSelector(label),
      toStatus(testResult),
      maybeThrowable(testResult),
      0L
    )

  def from(renderedResult: RenderedResult, fullyQualifiedName: String) = {
    val status = renderedResult.status match {
      case RenderedResult.Status.Failed  => Status.Failure
      case RenderedResult.Status.Passed  => Status.Success
      case RenderedResult.Status.Ignored => Status.Ignored
    }
    ZTestEvent(fullyQualifiedName, new TestSelector(renderedResult.label), status, None, 0)
  }

  private def maybeThrowable(testResult: TestResult) = testResult match {
    case Assertion.Failure(FailureDetails.Runtime(cause)) =>
      Some(cause.squashWith {
        case t: Throwable => t
        case other        => new IllegalStateException(s"cause is not throwable, but: ${other.getClass.getName}")
      })
    case _ => None
  }

  private def toStatus(testResult: TestResult) = testResult match {
    case Assertion.Failure(_) => Status.Failure
    case Assertion.Success    => Status.Success
    case Assertion.Ignore     => Status.Ignored
  }
}
