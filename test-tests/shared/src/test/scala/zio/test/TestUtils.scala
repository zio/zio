package zio.test

import zio.{ExecutionStrategy, Scope, UIO}

object TestUtils {

  def execute[E](spec: ZSpec[TestEnvironment, E]): UIO[Summary] =
    TestExecutor
      .default(testEnvironment)
      .run(spec, ExecutionStrategy.Sequential)
      .provide(testEnvironment, Scope.default)

  def isIgnored[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec)
      .map(_.ignore > 0)

  def succeeded[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { summary =>
      summary.fail == 0
    }
}
