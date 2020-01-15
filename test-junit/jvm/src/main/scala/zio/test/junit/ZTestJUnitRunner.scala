package zio.test.junit

import com.github.ghik.silencer.silent
import org.junit.runner.manipulation.{ Filter, Filterable }
import org.junit.runner.notification.{ Failure, RunNotifier }
import org.junit.runner.{ Description, RunWith, Runner }

import zio.ZIO.effectTotal
import zio._
import zio.test.FailureRenderer.FailureMessage.Message
import zio.test.Spec.{ SpecCase, SuiteCase, TestCase }
import zio.test.TestFailure.{ Assertion, Runtime }
import zio.test.TestSuccess.{ Ignored, Succeeded }
import zio.test._

/**
 * Custom JUnit 4 runner for ZIO Test Specs.<br/>
 * Any instance of [[zio.test.AbstractRunnableSpec]], that is a class (JUnit won't run objects),
 * if annotated with `@RunWith(classOf[ZTestJUnitRunner])` can be run by IDEs and build tools that support JUnit.<br/>
 * Your spec can also extend [[JUnitRunnableSpec]] to inherit the annotation.
 * In order to expose the structure of the test to JUnit (and the external tools), `getDescription` has to execute Suite level effects.
 * This means that these effects will be executed twice (first in `getDescription` and then in `run`).
 * <br/><br/>
 * Scala.JS is not supported, as JUnit TestFramework for SBT under Scala.JS doesn't support custom runners.
 */
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
              .ignore
        case TestCase(label, _) =>
          effectTotal(description.addChild(testDescription(label.toString, path)))
      }

    unsafeRun(
      traverse(filteredSpec, description)
        .provideManaged(spec.runner.executor.environment)
    )
    description
  }

  override def run(notifier: RunNotifier): Unit =
    zio.Runtime((), spec.runner.platform).unsafeRun {
      val instrumented = instrumentSpec(filteredSpec, new JUnitNotifier(notifier))
      spec.runner.run(instrumented).unit.provideManaged(spec.runner.bootstrap)
    }

  private def reportRuntimeFailure[S, R, L](notifier: JUnitNotifier, path: Vector[String], label: R, cause: Cause[L]) =
    for {
      rendered <- FailureRenderer.renderCause(cause, 0).map(renderToString)
      _        <- notifier.fireTestFailure(label, path, rendered, cause.dieOption.orNull)
    } yield ()

  private def reportAssertionFailure[L](
    notifier: JUnitNotifier,
    path: Vector[String],
    label: L,
    result: TestResult
  ): ZIO[Any, Nothing, Unit] =
    FailureRenderer
      .renderTestFailure("", result)
      .flatMap { rendered =>
        notifier.fireTestFailure(label, path, renderToString(rendered))
      }

  private def testDescription(label: String, path: Vector[String]) = {
    val uniqueId = path.mkString(":") + ":" + label
    Description.createTestDescription(className, label, uniqueId)
  }

  private def instrumentSpec[R, E, L, S](
    zspec: ZSpec[R, E, L, S],
    notifier: JUnitNotifier
  ): ZSpec[R, E, L, S] = {
    type ZSpecCase = SpecCase[R, TestFailure[E], L, TestSuccess[S], Spec[R, TestFailure[E], L, TestSuccess[S]]]
    def instrumentTest(label: L, path: Vector[String], test: ZIO[R, TestFailure[E], TestSuccess[S]]) =
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
        case TestCase(label, test) => TestCase(label, instrumentTest(label, path, test))
        case SuiteCase(label, specs, es) =>
          @silent("inferred to be `Any`")
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

  private class JUnitNotifier(notifier: RunNotifier) {
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
abstract class JUnitRunnableSpec extends DefaultRunnableSpec
