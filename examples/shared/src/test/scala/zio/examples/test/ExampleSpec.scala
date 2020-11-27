package zio.examples.test

import zio.test.{ assert, suite, test, Assertion, DefaultRunnableSpec }

object ExampleSpec extends DefaultRunnableSpec {

  def spec = suite("some suite")(
    test("failing test") {
      val stuff = 1
      assert(stuff)(Assertion.equalTo(2))
    },
    test("passing test") {
      assert(1)(Assertion.equalTo(1))
    }
  )
}
