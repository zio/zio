package zio

import zio.test._

object ZStateSpec extends ZIOBaseSpec {

  def spec =
    suite("ZStateSpec")(
      test("state can be updated") {
        ZIO.stateful(0) {
          for {
            _     <- ZIO.updateState[Int](_ + 1)
            state <- ZIO.getState[Int]
          } yield assertTrue(state == 1)
        }
      },
      test("state can be joined between fibers") {
        ZIO.statefulWith(0)(fork = identity, join = _ + _) {
          for {
            fiber <- ZIO.forkAllDiscard(List.fill(100)(ZIO.updateState[Int](_ + 1)))
            _     <- fiber.join
            state <- ZIO.getState[Int]
          } yield assertTrue(state == 100)
        }
      }
    )
}
