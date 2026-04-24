package zio.test

import zio.{ExecutionStrategy, Random, UIO, ZIO}

object TestUtils {

  def execute[E](spec: Spec[TestEnvironment, E]): UIO[Summary] = for {
    randomId <- ZIO.withRandom(Random.RandomLive)(Random.nextInt).map("test_case_" + _)
    summary  <- defaultTestRunner.executor.run(randomId, spec, ExecutionStrategy.Sequential)
  } yield summary

  def isIgnored[E](spec: Spec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec)
      .map(_.ignore > 0)

  def succeeded[E](spec: Spec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { summary =>
      summary.fail == 0
    }
}
