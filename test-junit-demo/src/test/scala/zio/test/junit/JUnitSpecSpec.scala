package zio.test.junit;

import zio.test._

class Foo extends JUnitRunnableSpec {
  def spec =
    suite("Foo")(
      test("test") {
        assertTrue(false)
      }
    )
}