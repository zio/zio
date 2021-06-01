package zio.examples.test

import zio.test.junit.JUnitRunnableSpec
import zio.test._

class ExampleSpecWithJUnit extends JUnitRunnableSpec {
  def spec = suite("some suite")(
    test("failing test") {
      assert(1)(Assertion.equalTo(2))
    },
    test("passing test") {
      assert(1)(Assertion.equalTo(1))
    },
    test("failing test assertTrue") {
      val one = 1
      assertTrue(one == 2)
    },
    test("passing test assertTrue") {
      assertTrue(1 == 1)
    }
  )
}
