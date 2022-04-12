package zio.test

import zio.{Console, ExecutionStrategy, Scope, UIO, ZEnv, ZIOAppArgs}

object TestUtils {

  def execute[E](spec: ZSpec[TestEnvironment, E]): UIO[Summary] =
    TestExecutor
      .default(
        Scope.default >>> testEnvironment,
        (ZEnv.live ++ Scope.default) >+> TestEnvironment.live ++ ZIOAppArgs.empty, //  TODO
        (Console.live >>> TestLogger.fromConsole(
          Console.ConsoleLive
        ) >>> ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live)
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
