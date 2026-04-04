package zio.test

import zio.test._

object GenElementsReproduction extends ZIOSpecDefault {
  def spec = suite("GenElementsReproduction")(
    test("checkAll(Gen.elements(1, 1, 1, 2)) should check all unique values") {
      checkAll(Gen.elements(1, 1, 1, 2)) { i =>
        assertTrue(i == 1 || i == 2)
      }
    },
    test("checkAll(Gen.elements(1, 1, 2)) should check all unique values") {
      checkAll(Gen.elements(1, 1, 2)) { i =>
        assertTrue(i == 1 || i == 2)
      }
    }
  )
}
