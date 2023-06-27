package zio

import zio.test._

object A extends ZIOSpecDefault {
  def spec = suite("A")(
    test("test") {
      assertTrue(1 + 1 == 3)
    },

    test("test with check in A") {
      check(Gen.int) { _ =>
        assertTrue(1 + 1 == 2)
      }
    }
  )
}