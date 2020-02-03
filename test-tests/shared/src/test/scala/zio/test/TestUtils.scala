package zio.test

import zio.test.environment.TestEnvironment
import zio.{ UIO, ZIO }

object TestUtils {

  def execute[E, S](spec: ZSpec[TestEnvironment, E, S]): UIO[ExecutedSpec[E, S]] =
    TestExecutor.managed(environment.testEnvironmentManaged).run(spec, ExecutionStrategy.Sequential)

  def forAllTests[E, S](
    execSpec: UIO[ExecutedSpec[E, S]]
  )(f: Either[TestFailure[E], TestSuccess[S]] => Boolean): ZIO[Any, Nothing, Boolean] =
    execSpec.flatMap { results =>
      results.forall { case Spec.TestCase(_, test, _) => test.map(r => f(r)); case _ => ZIO.succeedNow(true) }
    }

  def isIgnored[E, S](spec: ZSpec[environment.TestEnvironment, E, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
  }

  def isSuccess[E, S](spec: ZSpec[environment.TestEnvironment, E, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
  }
}
