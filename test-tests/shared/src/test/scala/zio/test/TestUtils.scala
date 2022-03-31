package zio.test

import zio.{Console, ExecutionStrategy, UIO}

object TestUtils {

  def execute[E](spec: ZSpec[TestEnvironment, E]): UIO[Summary] =
    TestExecutor
      .default(
        testEnvironment,
        (Console.live >>> TestLogger.fromConsole >>> ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live)
      )
      .run(spec, ExecutionStrategy.Sequential)

  def isIgnored[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec)
      .map(_.ignore > 0)

  def succeeded[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { summary =>
      summary.fail == 0
    }
}
