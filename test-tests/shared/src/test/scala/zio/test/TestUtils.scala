package zio.test

import zio.{ExecutionStrategy, UIO}

object TestUtils {

  def execute[E](spec: Spec[TestEnvironment, E]): UIO[Summary] =
    defaultTestRunner.executor.run(spec, ExecutionStrategy.Sequential)

  def isIgnored[E](spec: Spec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec)
      .map(_.ignore > 0)

  def succeeded[E](spec: Spec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { summary =>
      summary.fail == 0
    }
}
