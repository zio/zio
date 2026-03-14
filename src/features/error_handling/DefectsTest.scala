package features.error_handling

import zio.test._
import zio.{UIO, ZIO, Task}

object DefectsTest extends ZIOSpecDefault {

  def spec = suite("Defects Handling")(
    test("catchAll should handle defects properly") {
      val faultyEffect: ZIO[Any, Nothing, String] = ZIO.fail(new RuntimeException("Test error"))

      val result: ZIO[Any, Nothing, String] = faultyEffect.catchAll(_ => UIO.succeed("Recovered"))

      assertZIO(result)(equalTo("Recovered"))
    },

    test("fold should handle defects properly") {
      val faultyEffect: ZIO[Any, Nothing, String] = ZIO.fail(new RuntimeException("Test error"))

      val result: ZIO[Any, Nothing, String] = faultyEffect.fold(_ => "Recovered", identity)

      assert(result)(equalTo("Recovered"))
    }
  )
}