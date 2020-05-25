package zio.test

import zio.test.environment.TestEnvironment
import zio.{ ExecutionStrategy, UIO, ZIO }

object TestUtils {

  def execute[E](spec: ZSpec[TestEnvironment, E]): UIO[ExecutedSpec[E]] =
    TestExecutor.default(environment.testEnvironment).run(spec, ExecutionStrategy.Sequential)

  def isIgnored[E](spec: ZSpec[environment.TestEnvironment, E]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    execSpec.map(_.forAllTests {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    })
  }

  def succeeded[E](spec: ZSpec[environment.TestEnvironment, E]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    execSpec.map(_.forAllTests {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    })
  }
}
