package zio.examples.test

import zio.test.{ assert, suite, test, Assertion, DefaultRunnableSpec }

object ExampleSpec extends DefaultRunnableSpec {

  def spec = suite("some suite")(
    test("failing test") {
      val stuff = 1
      assert(stuff)(Assertion.equalTo(2))
    },
    test("failing test 2") {
      val stuff = Some(1)
      assert(stuff)(Assertion.isSome(Assertion.equalTo(2)))
    },
    test("failing test 3") {
      val stuff = Some(1)
      assert(stuff)(Assertion.isSome(Assertion.not(Assertion.equalTo(1))))
    },
    test("passing test") {
      assert(1)(Assertion.equalTo(1))
    }
  )
}
