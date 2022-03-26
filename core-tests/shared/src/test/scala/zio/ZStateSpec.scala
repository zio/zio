package zio

import zio.test._

object ZStateSpec extends ZIOSpecDefault {

  def spec =
    suite("ZStateSpec")(
      test("state can be updated") {
        ZIO.stateful(0) {
          for {
            _     <- ZIO.updateState[Int](_ + 1)
            state <- ZIO.getState[Int]
          } yield assertTrue(state == 1)
        }
      }
    )
}
