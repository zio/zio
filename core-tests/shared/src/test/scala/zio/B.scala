package zio

import zio.test._

object B extends ZIOSpecDefault {
  def spec = suite("B")(
    test("test with check in B") {
      check(Gen.int) { _ =>
        assertTrue(1 + 1 == 2)
      }
    }
  )
}