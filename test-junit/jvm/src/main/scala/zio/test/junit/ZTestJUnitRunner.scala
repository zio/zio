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
class ZTestJUnitRunner(klass: Class[_]) extends Runner with Filterable with BootstrapRuntime {
  private val className = klass.getName.stripSuffix("$")

  private lazy val spec: AbstractRunnableSpec = {
    klass
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[AbstractRunnableSpec]
  }

  private var filter = Filter.ALL

  lazy val getDescription: Description = {
    val description = Description.createSuiteDescription(className)
    def traverse[R, E](
      spec: ZSpec[R, E],
      description: Description,
      path: Vector[String] = Vector.empty
    ): ZManaged[R, Any, Unit] =
      spec.caseValue match {
        case SuiteCase(label, specs, _) =>
          val suiteDesc = Description.createSuiteDescription(label, path.mkString(":"))
          ZManaged.effectTotal(description.addChild(suiteDesc)) *>
            specs
              .flatMap(ZManaged.foreach(_)(traverse(_, suiteDesc, path :+ label)))
              .ignore
        case TestCase(label, _, _) =>
          ZManaged.effectTotal(description.addChild(testDescription(label, path)))
      }

    unsafeRun(
      traverse(filteredSpec, description)
        .provideLayer(spec.runner.executor.environment)
        .useNow
    )
    description
  }

  override def run(notifier: RunNotifier): Unit =
    zio.Runtime((), spec.runner.platform).unsafeRun {
      val instrumented = instrumentSpec(filteredSpec, new JUnitNotifier(notifier))
      spec.runner.run(instrumented).unit.provideLayer(spec.runner.bootstrap)
    }

  private def reportRuntimeFailure[E](
    notifier: JUnitNotifier,
    path: Vector[String],
    label: String,
    cause: Cause[E]
  ): UIO[Unit] = {
    val rendered = renderToString(FailureRenderer.renderCause(cause, 0))
    notifier.fireTestFailure(label, path, rendered, cause.dieOption.orNull)
  }

  private def reportAssertionFailure(
    notifier: JUnitNotifier,
    path: Vector[String],
    label: String,
    result: TestResult
  ): UIO[Unit] = {
    val rendered = FailureRenderer.renderTestFailure("", result)
    notifier.fireTestFailure(label, path, renderToString(rendered))
  }

  private def testDescription(label: String, path: Vector[String]) = {
    val uniqueId = path.mkString(":") + ":" + label
    Description.createTestDescription(className, label, uniqueId)
  }

  private def instrumentSpec[R, E](
    zspec: ZSpec[R, E],
    notifier: JUnitNotifier
  ): ZSpec[R, E] = {
    type ZSpecCase = SpecCase[R, TestFailure[E], TestSuccess, Spec[R, TestFailure[E], TestSuccess]]
    def instrumentTest(label: String, path: Vector[String], test: ZIO[R, TestFailure[E], TestSuccess]) =
      notifier.fireTestStarted(label, path) *> test.tapBoth(
        {
          case Assertion(result) => reportAssertionFailure(notifier, path, label, result)
          case Runtime(cause)    => reportRuntimeFailure(notifier, path, label, cause)
        },
        {
          case Succeeded(_) => notifier.fireTestFinished(label, path)
          case Ignored      => notifier.fireTestIgnored(label, path)
        }
      )
    def loop(specCase: ZSpecCase, path: Vector[String] = Vector.empty): ZSpecCase =
      specCase match {
        case TestCase(label, test, annotations) => TestCase(label, instrumentTest(label, path, test), annotations)
        case SuiteCase(label, specs, es) =>
          @silent("inferred to be `Any`")
          val instrumented =
            specs.flatMap(ZManaged.foreach(_)(s => ZManaged.succeedNow(Spec(loop(s.caseValue, path :+ label)))))
          SuiteCase(label, instrumented.map(_.toVector), es)
      }
    Spec(loop(zspec.caseValue))
  }

  private def filteredSpec: ZSpec[spec.Environment, spec.Failure] =
    spec.spec
      .filterLabels(l => filter.shouldRun(testDescription(l, Vector.empty)))
      .getOrElse(spec.spec)

  override def filter(filter: Filter): Unit =
    this.filter = filter

  private def renderToString(message: Message) =
    message.lines.map {
      _.fragments.map(_.text).fold("")(_ + _)
    }.mkString("\n")

  private class JUnitNotifier(notifier: RunNotifier) {
    def fireTestFailure(
      label: String,
      path: Vector[String],
      renderedText: String,
      throwable: Throwable = null
    ): UIO[Unit] =
      effectTotal {
        notifier.fireTestFailure(
          new Failure(testDescription(label, path), new TestFailed(renderedText, throwable))
        )
      }

    def fireTestStarted(label: String, path: Vector[String]): UIO[Unit] = effectTotal {
      notifier.fireTestStarted(testDescription(label, path))
    }

    def fireTestFinished(label: String, path: Vector[String]): UIO[Unit] = effectTotal {
      notifier.fireTestFinished(testDescription(label, path))
    }

    def fireTestIgnored(label: String, path: Vector[String]): UIO[Unit] = effectTotal {
      notifier.fireTestIgnored(testDescription(label, path))
    }
  }
}

private[junit] class TestFailed(message: String, cause: Throwable = null)
    extends Throwable(message, cause, false, false)

@RunWith(classOf[ZTestJUnitRunner])
abstract class JUnitRunnableSpec extends DefaultRunnableSpec
