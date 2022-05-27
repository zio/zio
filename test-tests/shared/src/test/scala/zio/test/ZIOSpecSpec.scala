package zio.test

import zio._

object ZIOSpecSpec extends ZIOSpecDefault {

  val global =
    scala.concurrent.ExecutionContext.global

  val expected =
    Executor.fromExecutionContext(Runtime.defaultYieldOpCount)(global)

  override val bootstrap =
    testEnvironment ++ Runtime.setBlockingExecutor(expected)

  def spec = suite("ZIOAppSpec")(
    test("Runtime can be modified using bootstrap") {
      for {
        actual <- ZIO.blockingExecutor
      } yield assertTrue(actual == expected)
    }
  )
}
