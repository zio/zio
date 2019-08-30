package zio.examples.test

import zio.test.{ Assertion, DefaultRunnableSpec, assert, suite, test}

object ExampleSpec extends DefaultRunnableSpec(
  suite("some suite") (
    test("failing test") {
      assert(1, Assertion.equalTo(2))
    },
    test("passing test") {
      assert(1, Assertion.equalTo(1))
    }
  )
)