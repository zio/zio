package zio.test

import zio.test.environment.TestEnvironment
import zio.{ UIO, ZIO }

object TestUtils {

  def execute[E, L, S](spec: ZSpec[TestEnvironment, E, L, S]): UIO[ExecutedSpec[E, L, S]] =
    TestExecutor.managed(environment.testEnvironmentManaged).run(spec, ExecutionStrategy.Sequential)

  def forAllTests[E, L, S](
    execSpec: UIO[ExecutedSpec[E, L, S]]
  )(f: Either[TestFailure[E], TestSuccess[S]] => Boolean): ZIO[Any, Nothing, Boolean] =
    execSpec.flatMap { results =>
      results.forall { case Spec.TestCase(_, test) => test.map(r => f(r._1)); case _ => ZIO.succeed(true) }
    }

  def isIgnored[E, L, S](spec: ZSpec[environment.TestEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
  }

  def isSuccess[E, L, S](spec: ZSpec[environment.TestEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
  }
}
