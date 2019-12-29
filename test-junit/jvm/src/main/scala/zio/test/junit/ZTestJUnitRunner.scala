package zio.test.junit

import org.junit.runner.manipulation.{ Filter, Filterable }
import org.junit.runner.notification.{ Failure, RunNotifier }
import org.junit.runner.{ Description, RunWith, Runner }
import zio.ZIO.effectTotal
import zio._
import zio.clock.Clock
import zio.test.FailureRenderer.FailureMessage.Message
import zio.test.Spec.{ SpecCase, SuiteCase, TestCase }
import zio.test.TestFailure.{ Assertion, Runtime }
import zio.test.TestSuccess.{ Ignored, Succeeded }
import zio.test._

class ZTestJUnitRunner(klass: Class[_]) extends Runner with Filterable with DefaultRuntime {
  private val className = klass.getName.stripSuffix("$")

  private lazy val spec: AbstractRunnableSpec = {
    klass
      .newInstance()
      .asInstanceOf[AbstractRunnableSpec]
  }

  private var filter = Filter.ALL

  lazy val getDescription: Description = {
    val description = Description.createSuiteDescription(className)
    def traverse[R, E, L, S](
      spec: ZSpec[R, E, L, S],
      description: Description,
      path: Vector[String] = Vector.empty
    ): URIO[R, Unit] =
      spec.caseValue match {
        case SuiteCase(label, specs, _) =>
          val suiteDesc = Description.createSuiteDescription(label.toString, path.mkString(":"))
          effectTotal(description.addChild(suiteDesc)) *>
            specs
              .flatMap(ZIO.foreach(_)(traverse(_, suiteDesc, path :+ label.toString)))
              .catchAll(_ => ZIO.unit)
              .unit
        case TestCase(label, _) =>
          effectTotal(description.addChild(testDescription(label.toString, path)))
      }

    unsafeRun(
      traverse(filteredSpec, description)
        .provideManaged(spec.runner.executor.environment)
    )
    description
  }

  override def run(notifier: RunNotifier): Unit = {
    val env = new TestLogger with Clock {
      override def testLogger: TestLogger.Service = spec.runner.defaultTestLogger.testLogger
      override val clock: Clock.Service[Any]      = Clock.Live.clock
    }
    zio
      .Runtime(env, spec.runner.platform)
      .unsafeRun {
        val instrumented = instrumentSpec(filteredSpec, notifier)
        spec.runner.run(instrumented).unit
      }
  }

  private def reportRuntimeFailure[S, R, L](notifier: RunNotifier, path: Vector[String], label: R, cause: Cause[L]) =
    for {
      rendered <- FailureRenderer.renderCause(cause, 0).map(renderToString)
      _        <- notifier.fireTestFailure(label, path, rendered, cause.dieOption.orNull)
    } yield ()

  private def reportAssertionFailure[S, R, L](
    notifier: RunNotifier,
    path: Vector[String],
    label: R,
    result: TestResult
  ) =
    FailureRenderer
      .renderTestFailure("", result)
      .flatMap { rendered =>
        notifier.fireTestFailure(label, path, renderToString(rendered))
      }

  private def testDescription(label: String, path: Vector[String]) = {
    val uniqueId = path.mkString(":") + ":" + label
    Description.createTestDescription(path.headOption.getOrElse(className), label, uniqueId)
  }

  private def instrumentSpec[R, E, L, S](
    zspec: ZSpec[R, E, L, S],
    notifier: RunNotifier,
    path: Vector[String] = Vector.empty
  ): ZSpec[R, E, L, S] = {
    type ZSpecCase = SpecCase[R, TestFailure[E], L, TestSuccess[S], Spec[R, TestFailure[E], L, TestSuccess[S]]]
    def instrumentTest(label: L, test: ZIO[R, TestFailure[E], TestSuccess[S]]) =
      notifier.fireTestStarted(label, path) *> test.tapBoth(
        {
          case Assertion(result) => reportAssertionFailure(notifier, path, label, result)
          case Runtime(cause)    => reportRuntimeFailure(notifier, path, label, cause)
        }, {
          case Succeeded(_) => notifier.fireTestFinished(label, path)
          case Ignored      => notifier.fireTestIgnored(label, path)
        }
      )
    def loop(specCase: ZSpecCase, path: Vector[String] = Vector.empty): ZSpecCase =
      specCase match {
        case TestCase(label, test) => TestCase(label, instrumentTest(label, test))
        case SuiteCase(label, specs, es) =>
          val instrumented =
            specs.flatMap(ZIO.foreach(_)(s => ZIO.succeed(Spec(loop(s.caseValue, path :+ label.toString)))))
          SuiteCase(label, instrumented.map(_.toVector), es)
      }
    Spec(loop(zspec.caseValue))
  }

  private def filteredSpec: ZSpec[spec.Environment, spec.Failure, spec.Label, spec.Test] =
    spec.spec
      .filterLabels(l => filter.shouldRun(testDescription(l.toString, Vector.empty)))
      .getOrElse(spec.spec)

  override def filter(filter: Filter): Unit =
    this.filter = filter

  private def renderToString(message: Message) =
    message.lines.map {
      _.fragments.map(_.text).fold("")(_ + _)
    }.mkString("\n")

  private implicit class NotifierOps(val notifier: RunNotifier) {
    def fireTestFailure[L](
      label: L,
      path: Vector[String],
      renderedText: String,
      throwable: Throwable = null
    ): UIO[Unit] =
      effectTotal {
        notifier.fireTestFailure(
          new Failure(testDescription(label.toString, path), new TestFailed(renderedText, throwable))
        )
      }

    def fireTestStarted[L](label: L, path: Vector[String]): UIO[Unit] = effectTotal {
      notifier.fireTestStarted(testDescription(label.toString, path))
    }

    def fireTestFinished[L](label: L, path: Vector[String]): UIO[Unit] = effectTotal {
      notifier.fireTestFinished(testDescription(label.toString, path))
    }

    def fireTestIgnored[L](label: L, path: Vector[String]): UIO[Unit] = effectTotal {
      notifier.fireTestIgnored(testDescription(label.toString, path))
    }
  }
}

private[junit] class TestFailed(message: String, cause: Throwable = null)
    extends Throwable(message, cause, false, false)

@RunWith(classOf[ZTestJUnitRunner])
abstract class DefaultSpecWithJUnit extends DefaultRunnableSpec
