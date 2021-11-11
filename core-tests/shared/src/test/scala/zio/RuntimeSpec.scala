package zio

import zio.test._

object RuntimeSpec extends ZIOBaseSpec {
  def spec = suite("RuntimeSpec") {
    test("example") {
      assertTrue(true)
    }
  }
}
