/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

package zio.test.junit

import org.junit.runner.manipulation.{Filter, Filterable}
import org.junit.runner.notification.{Failure, RunNotifier}
import org.junit.runner.{Description, RunWith, Runner}
import zio.ZIO.succeed
import zio._
import zio.test.TestFailure.{Assertion, Runtime}
import zio.test.TestSuccess.{Ignored, Succeeded}
import zio.test._
import zio.test.render.ConsoleRenderer
import zio.test.render.ExecutionResult.ResultType.Test
import zio.test.render.ExecutionResult.Status.Failed
import zio.test.render.LogLine.Message

/**
 * A JUnit 4 runner for ZIO Test specs. A spec class that extends
 * [[zio.test.ZIOSpecAbstract]] (such as `ZIOSpecDefault`) and is annotated with
 * `@RunWith(classOf[ZTestJUnitRunner])` runs in IDEs and build tools that run
 * JUnit 4 tests, such as Maven and Gradle. A spec class can also extend
 * [[JUnitRunnableSpec]], which carries the annotation.
 *
 * The runner creates the spec with its public no-argument constructor, so the
 * spec must be a class: a Scala object can't be run this way.
 *
 * {{{
 * import org.junit.runner.RunWith
 * import zio.test._
 *
 * @RunWith(classOf[ZTestJUnitRunner])
 * class AdditionSpec extends ZIOSpecDefault {
 *   def spec = suite("AdditionSpec")(
 *     test("adds two numbers")(assertTrue(1 + 1 == 2))
 *   )
 * }
 * }}}
 *
 * To show the structure of the spec to JUnit, `getDescription` runs the effects
 * that build suites (for example, a suite built from a ZIO effect). `run` runs
 * these effects again, so they run twice.
 *
 * The module is available on the JVM only.
 *
 * @see
 *   [[https://zio.dev/reference/test/junit-integration/ JUnit Integration]]
 */
class ZTestJUnitRunner(klass: Class[_]) extends Runner with Filterable {

  private val className = klass.getName.stripSuffix("$")

  private lazy val spec: ZIOSpecAbstract =
    klass
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[ZIOSpecAbstract]

  private var filter = Filter.ALL

  /**
   * Returns the JUnit description of the spec: a suite named after the spec
   * class, with a child suite for each suite of the spec and a child test for
   * each test, whose method name is the test label.
   *
   * It is computed the first time it is used, with the live environment, the
   * test environment and the `bootstrap` layer of the spec, and it runs the
   * effects that build suites. Tests that the filter (see `filter`) removes are
   * left out.
   */
  lazy val getDescription: Description = {
    val description = Description.createSuiteDescription(className)
    def traverse[R, E](
      spec: Spec[R, E],
      description: Description,
      path: Vector[String] = Vector.empty
    ): ZIO[R with Scope, Any, Unit] =
      spec.caseValue match {
        case Spec.ExecCase(_, spec)        => traverse(spec, description, path)
        case Spec.LabeledCase(label, spec) => traverse(spec, description, path :+ label)
        case Spec.ScopedCase(scoped)       => scoped.flatMap(traverse(_, description, path))
        case Spec.MultipleCase(specs) =>
          val suiteDesc = Description.createSuiteDescription(path.lastOption.getOrElse(""), path.mkString(":"))
          ZIO.succeed(description.addChild(suiteDesc)) *>
            ZIO.foreach(specs)(traverse(_, suiteDesc, path)).ignore
        case Spec.TestCase(_, _) =>
          ZIO.succeed(description.addChild(testDescription(path.lastOption.getOrElse(""), path)))
      }

    val scoped =
      ZIO.scoped[ZTestJUnitRunner.this.spec.Environment with zio.test.TestEnvironment](
        traverse(filteredSpec, description)
      )

    zio.Runtime.default.unsafe
      .run(
        scoped
          .provide(
            Scope.default >>> (liveEnvironment >>> TestEnvironment.live <*> ZLayer.environment[Scope]),
            spec.bootstrap
          )
      )(Trace.empty, Unsafe.unsafe)
      .getOrThrowFiberFailure()(Unsafe.unsafe)
    description
  }

  /**
   * Runs the spec and reports each test to `notifier`. A passed test is
   * reported as started and finished, and a failed test as started, failed and
   * finished; the failure of a test that dies has the defect as its cause. An
   * ignored test is reported as ignored.
   *
   * The spec runs with the live environment, the test environment and its
   * `bootstrap` layer. Tests that the filter (see `filter`) removes don't run.
   */
  override def run(notifier: RunNotifier): Unit = {
    val _ = zio.Runtime.default.unsafe.run {

      val instrumented: Spec[spec.Environment with TestEnvironment with Scope, Any] =
        instrumentSpec(filteredSpec, new JUnitNotifier(notifier))
      spec
        .runSpecAsApp(instrumented, TestArgs.empty, Console.ConsoleLive)
        .provide(
          Scope.default >>> (liveEnvironment >>> (TestEnvironment.live <*> ZLayer
            .environment[Scope]) +!+ spec.bootstrap)
        )
    }(Trace.empty, Unsafe.unsafe).getOrThrowFiberFailure()(Unsafe.unsafe)
  }

  private def reportRuntimeFailure[E](
    notifier: JUnitNotifier,
    path: Vector[String],
    label: String,
    cause: Cause[E]
  ): UIO[Unit] = {
    val rendered = renderToString(ConsoleRenderer.renderCause(cause, 0))
    notifier.fireTestFailure(label, path, rendered, cause.dieOption.orNull) *> notifier.fireTestFinished(label, path)
  }

  private def reportAssertionFailure(
    notifier: JUnitNotifier,
    path: Vector[String],
    label: String,
    result: TestResult
  ): UIO[Unit] = {
    val rendered = renderFailureDetails(label, result)
    notifier.fireTestFailure(label, path, renderToString(rendered)) *> notifier.fireTestFinished(label, path)
  }

  private def renderFailureDetails(label: String, result: TestResult): Message =
    Message(
      ConsoleRenderer
        .rendered(
          Test,
          label,
          Failed,
          0,
          ConsoleRenderer.renderAssertionResult(result.result, 0).lines: _*
        )
        .streamingLines
    )

  private def testDescription(label: String, path: Vector[String]): Description = {
    val uniqueId = path.mkString(":") + ":" + label
    Description.createTestDescription(className, label, uniqueId)
  }

  private def instrumentSpec[R, E](
    zspec: Spec[R, E],
    notifier: JUnitNotifier
  ): Spec[R, E] = {
    type ZSpecCase = Spec.SpecCase[R, E, Spec[R, E]]
    def instrumentTest(label: String, path: Vector[String], test: ZIO[R, TestFailure[E], TestSuccess]) =
      test
        .tapBoth(
          {
            case Assertion(result, _) =>
              notifier.fireTestStarted(label, path) *>
                reportAssertionFailure(notifier, path, label, result)
            case Runtime(cause, _) =>
              notifier.fireTestStarted(label, path) *>
                reportRuntimeFailure(notifier, path, label, cause)
          },
          {
            case Succeeded(_) =>
              notifier.fireTestStarted(label, path) *>
                notifier.fireTestFinished(label, path)
            case Ignored(_) => notifier.fireTestIgnored(label, path)
          }
        )
        .tapDefect { e =>
          notifier.fireTestStarted(label, path) *>
            reportRuntimeFailure(notifier, path, label, e)
        }
    def loop(specCase: ZSpecCase, path: Vector[String] = Vector.empty): ZSpecCase =
      specCase match {
        case Spec.ExecCase(exec, spec) =>
          Spec.ExecCase(exec, Spec(loop(spec.caseValue, path)))
        case Spec.LabeledCase(label, spec) =>
          Spec.LabeledCase(label, Spec(loop(spec.caseValue, path :+ label)))
        case Spec.ScopedCase(scoped) =>
          Spec.ScopedCase[R, E, Spec[R, E]](
            scoped.map(spec => Spec(loop(spec.caseValue, path)))
          )
        case Spec.MultipleCase(specs) =>
          Spec.MultipleCase(specs.map(spec => Spec(loop(spec.caseValue, path))))
        case Spec.TestCase(test, annotations) =>
          Spec.TestCase(instrumentTest(path.lastOption.getOrElse(""), path, test), annotations)

      }
    Spec(loop(zspec.caseValue))
  }

  private def filteredSpec =
    spec.spec
      .filterLabels(l => filter.shouldRun(testDescription(l, Vector.empty)))
      .getOrElse(spec.spec)

  /**
   * Sets the filter that selects the tests to describe and run. Each label of
   * the spec is passed to `filter` as a test description whose class is the
   * spec class and whose method name is the label. A test is kept when its
   * label or the label of an enclosing suite is accepted. When the filter
   * accepts no label, the whole spec is kept.
   */
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
      succeed {
        notifier.fireTestFailure(
          new Failure(testDescription(label, path), new TestFailed(renderedText, throwable))
        )
      }

    def fireTestStarted(label: String, path: Vector[String]): UIO[Unit] = succeed {
      notifier.fireTestStarted(testDescription(label, path))
    }

    def fireTestFinished(label: String, path: Vector[String]): UIO[Unit] = succeed {
      notifier.fireTestFinished(testDescription(label, path))
    }

    def fireTestIgnored(label: String, path: Vector[String]): UIO[Unit] = succeed {
      notifier.fireTestIgnored(testDescription(label, path))
    }
  }
}

/**
 * The exception that a JUnit failure of a ZIO test carries: its message is the
 * rendered failure, and its cause is the defect when the test died.
 */
private[junit] class TestFailed(message: String, cause: Throwable = null)
    extends Throwable(message, cause, false, false)

/**
 * A `ZIOSpecDefault` annotated with `@RunWith(classOf[ZTestJUnitRunner])`, so
 * that JUnit 4 runs the classes that extend it with [[ZTestJUnitRunner]].
 *
 * Extend it with a class: JUnit 4 can't run an object with this runner. An
 * object that extends it is still a ZIO Test spec, which the ZIO Test sbt
 * framework runs.
 *
 * {{{
 * import zio.test._
 *
 * class StringSpec extends JUnitRunnableSpec {
 *   def spec = suite("StringSpec")(
 *     test("reverses a string")(assertTrue("abc".reverse == "cba"))
 *   )
 * }
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/junit-integration/ JUnit Integration]]
 */
@RunWith(classOf[ZTestJUnitRunner])
abstract class JUnitRunnableSpec extends ZIOSpecDefault
