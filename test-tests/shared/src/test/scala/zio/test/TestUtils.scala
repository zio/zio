package zio.test

import zio.{ExecutionStrategy, UIO}

object TestUtils {

  def execute[E](spec: ZSpec[TestEnvironment, E]): UIO[ExecutedSpec[E]] =
    TestExecutor.default(testEnvironment).run(spec, ExecutionStrategy.Sequential)

  def forAllTests[E](
    execSpec: ExecutedSpec[E]
  )(f: Either[TestFailure[E], TestSuccess] => Boolean): Boolean =
    execSpec.forall {
      case ExecutedSpec.TestCase(test, _) => f(test)
      case _                              => true
    }

  def isIgnored[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { executedSpec =>
      forAllTests(executedSpec) {
        case Right(TestSuccess.Ignored) => true
        case _                          => false
      }
    }

  def succeeded[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { executedSpec =>
      forAllTests(executedSpec) {
        case Right(TestSuccess.Succeeded(_)) => true
        case _                               => false
      }
    }
}
