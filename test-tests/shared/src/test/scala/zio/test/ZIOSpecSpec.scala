package zio.test

import zio._

object ZIOSpecSpec extends ZIOSpecDefault {

  val global =
    scala.concurrent.ExecutionContext.global

  val expected =
    Executor.fromExecutionContext(Runtime.defaultYieldOpCount)(global)

  def spec = suite("ZIOAppSpec")(
    test("RuntimeConfig can be modified using hook") {
      for {
        actual <- ZIO.blockingExecutor
      } yield assertTrue(actual == expected)
    }
  ) @@ TestAspect.ignore // TODO Investigate this for next PR
}
