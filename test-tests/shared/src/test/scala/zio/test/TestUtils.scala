package zio.test

import zio.test.environment.TestEnvironment
import zio.{ ExecutionStrategy, UIO, ZIO }

object TestUtils {

  def execute[E](spec: ZSpec[TestEnvironment, E]): UIO[ExecutedSpec[E]] =
    TestExecutor.default(environment.testEnvironment).run(spec, ExecutionStrategy.Sequential)

  def forAllTests[E](
    execSpec: UIO[ExecutedSpec[E]]
  )(f: Either[TestFailure[E], TestSuccess] => Boolean): UIO[Boolean] =
    execSpec.flatMap { results =>
      results.forall {
        case Spec.TestCase(_, test, _) => test.map(r => f(r))
        case _                         => ZIO.succeed(true)
      }.useNow
    }

  def isIgnored[E](spec: ZSpec[environment.TestEnvironment, E]): UIO[Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
  }

  def succeeded[E](spec: ZSpec[environment.TestEnvironment, E]): UIO[Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
  }
}
