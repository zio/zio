package zio.test

import zio.{Console, ExecutionStrategy, UIO}

object TestUtils {

  def execute[E](spec: Spec[TestEnvironment, E]): UIO[Summary] =
    TestExecutor
      .default(
        testEnvironment,
        (Console.live >>> TestLogger.fromConsole(
          Console.ConsoleLive
        ) >>> ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live)
      )
      .run(spec, ExecutionStrategy.Sequential)

  def isIgnored[E](spec: Spec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec)
      .map(_.ignore > 0)

  def succeeded[E](spec: Spec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { summary =>
      summary.fail == 0
    }
}
