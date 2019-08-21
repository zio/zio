package zio.examples.test

import zio.test.{DefaultRunnableSpec, Predicate, assert, suite, test}

object ExampleSpec extends DefaultRunnableSpec(
  suite("some suite") (
    test("failing test") {
      assert(1, Predicate.equalTo(2))
    },
    test("passing test") {
      assert(1, Predicate.equalTo(1))
    }
  )
)
