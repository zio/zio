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
import zio.test.Spec.{ SpecCase, SuiteCase, TestCase }
import zio.test._
import zio.{ RIO, Runtime, ZIO }

final class ZTestRunner(val args: Array[String], val remoteArgs: Array[String], testClassLoader: ClassLoader)
    extends Runner {
  def done(): String = "Done"
  def tasks(defs: Array[TaskDef]): Array[Task] =
    defs.map(new ZTestTask(_, testClassLoader))
}

class ZTestTask(val taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {
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
      _      <- result.foldM[Any, Throwable, Unit](ExecutionStrategy.Sequential)(handleSpecCase(eventHandler, loggers))
    } yield ()

    Runtime((), spec.platform).unsafeRunSync(handle)
    Array.empty
  }

  private def handleSpecCase(eventHandler: EventHandler, loggers: Array[Logger])(
    specCase: SpecCase[Any, TestResult, Unit]
  ) =
    specCase match {
      case SuiteCase(label, _, _)      => handleSuite(loggers)(label.toString)
      case TestCase(label, testResult) => handleTest(eventHandler, loggers)(label.toString, testResult)
    }

  private def handleSuite[R](loggers: Array[Logger])(label: String): RIO[R, Unit] =
    effects(loggers)(_.info(s"SUITE: $label"))

  private def handleTest[R](
    eventHandler: EventHandler,
    loggers: Array[Logger]
  )(label: String, result: TestResult): RIO[R, Unit] =
    for {
      _ <- effects(loggers)(_.info(s"TEST: $label: ${ZTestTask.describeResult(result)}"))
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
  def fromSpec(label: String, testResult: TestResult, fullyQualifiedName: String): Event =
    ZTestEvent(
      fullyQualifiedName,
      new TestSelector(label),
      toStatus(testResult),
      maybeThrowable(testResult),
      0L
    )

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

object ZTestTask {

  private def describeResult(testResult: TestResult) = testResult match {
    case f: Assertion.Failure[FailureDetails] => describeFailure(f.message)
    case Assertion.Success                    => "SUCCESS"
    case Assertion.Ignore                     => "IGNORED"
  }

  private def describeFailure(failureDetails: FailureDetails) = failureDetails match {
    case FailureDetails.Runtime(cause)         => cause.prettyPrint
    case FailureDetails.Predicate(fragment, _) => s"FAILURE: ${fragment.value} did not satisfy ${fragment.predicate}"
  }
}
