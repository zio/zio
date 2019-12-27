package zio.test.junit

import org.junit.runner.manipulation.{ Filter, Filterable }
import org.junit.runner.notification.{ Failure, RunNotifier }
import org.junit.runner.{ Description, RunWith, Runner }
import zio.ZIO.effectTotal
import zio.clock.Clock
import zio.test.FailureRenderer.FailureMessage.Message
import zio.test.Spec.{ SuiteCase, TestCase }
import zio.test.TestFailure.{ Assertion, Runtime }
import zio.test.TestSuccess.{ Ignored, Succeeded }
import zio.test.{ ExecutedSpec, _ }
import zio.{ DefaultRuntime, URIO, ZIO }

import scala.util.Try

class ZTestJUnitRunner(klass: Class[_]) extends Runner with Filterable with DefaultRuntime {

  private val className = klass.getName.stripSuffix("$")

  private lazy val spec: AbstractRunnableSpec = {
    Try(loadObject())
      .getOrElse(
        klass
          .newInstance()
          .asInstanceOf[AbstractRunnableSpec]
      )
  }

  private def loadObject(): AbstractRunnableSpec = {
    import org.portablescala.reflect._
    val fqn = className + "$"
    Reflect
      .lookupLoadableModuleClass(fqn, klass.getClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
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
      // we expect to be able to extract structure without the environment
        .provide(null.asInstanceOf[spec.Environment])
    )
    description
  }

  override def run(notifier: RunNotifier): Unit = {
    val env = new TestLogger with Clock {
      override def testLogger: TestLogger.Service = spec.runner.defaultTestLogger.testLogger
      override val clock: Clock.Service[Any]      = Clock.Live.clock
    }
    zio
      .Runtime((), spec.runner.platform)
      .unsafeRun(
        for {
          executedSpec <- spec.runner.run(filteredSpec).provide(env)
          _            <- notifyOnResults(notifier, executedSpec)
        } yield {}
      )
  }

  private def notifyOnResults[L, R, S](
    notifier: RunNotifier,
    executedSpec: ExecutedSpec[L, R, S],
    path: Vector[String] = Vector.empty
  ): ZIO[Any, Nothing, Unit] =
    executedSpec.caseValue match {
      case SuiteCase(label, specs, _) =>
        specs.flatMap(ZIO.foreach(_)(notifyOnResults(notifier, _, path :+ label.toString))).unit
      case TestCase(label, result) =>
        result.flatMap {
          case (Left(Assertion(result)), _) =>
            FailureRenderer
              .renderTestFailure("", result)
              .map(renderToString)
              .flatMap { text =>
                effectTotal {
                  notifier.fireTestFailure(new Failure(testDescription(label.toString, path), new TestFailed(text)))
                }
              }
          case (Left(Runtime(cause)), _) =>
            for {
              rendered <- FailureRenderer.renderCause(cause, 0).map(renderToString)
              _ <- effectTotal {
                    notifier.fireTestFailure(
                      new Failure(
                        testDescription(label.toString, path),
                        new TestFailed(rendered, cause.dieOption.orNull)
                      )
                    )
                  }
            } yield ()

          case (Right(Succeeded(_)), _) =>
            effectTotal { notifier.fireTestFinished(testDescription(label.toString, path)) }
          case (Right(Ignored), _) => effectTotal { notifier.fireTestIgnored(testDescription(label.toString, path)) }
        }
    }

  private def testDescription(label: String, path: Vector[String]) =
    Description.createTestDescription(className, label, path.mkString(":") + ":" + label)

  private def filteredSpec: ZSpec[spec.Environment, spec.Failure, spec.Label, spec.Test] =
    spec.spec
      .filterLabels(l => filter.shouldRun(testDescription(l.toString, Vector.empty)))
      .getOrElse(spec.spec)

  override def filter(filter: Filter): Unit =
    this.filter = filter

  private def renderToString(message: Message) =
    message.lines.map { line =>
      line.fragments.map(_.text).fold("")(_ + _)
    }.mkString("\n")
}

private[junit] class TestFailed(message: String, cause: Throwable = null)
    extends Throwable(message, cause, false, false)

@RunWith(classOf[ZTestJUnitRunner])
abstract class DefaultSpecWithJUnit extends DefaultRunnableSpec
