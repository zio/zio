package zio.examples.test

import zio.test.junit.JUnitRunnableSpec
import zio.test.{ assert, suite, test, Assertion }

class ExampleSpecWithJUnit extends JUnitRunnableSpec {
  def spec = suite("some suite")(
    test("failing test") {
      assert(1)(Assertion.equalTo(2))
    },
    test("passing test") {
      assert(1)(Assertion.equalTo(1))
    }
  )
}
