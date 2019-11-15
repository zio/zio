package zio.examples.test

import zio.test.{ assert, suite, test, Assertion, RunnableSpec }

object ExampleSpec extends RunnableSpec {

  def spec = suite("some suite")(
    test("failing test") {
      assert(1, Assertion.equalTo(2))
    },
    test("passing test") {
      assert(1, Assertion.equalTo(1))
    }
  )
}
