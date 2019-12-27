package zio.examples.test

import zio.test.junit.DefaultSpecWithJUnit
import zio.test.{Assertion, assert, suite, test}

class ExampleSpecWithJUnit extends DefaultSpecWithJUnit {
  def spec = suite("some suite")(
    test("failing test") {
      assert(1)(Assertion.equalTo(2))
    },
    test("passing test") {
      assert(1)(Assertion.equalTo(1))
    }
  )
}
