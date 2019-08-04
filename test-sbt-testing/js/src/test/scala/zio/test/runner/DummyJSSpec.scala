package zio.test.runner

import zio.test.{DefaultRunnableSpec, Predicate, assert, suite, test}

object DummyJSSpec extends DefaultRunnableSpec({
  suite("some suite") (
    test("failing test") {
      assert(1, Predicate.equals(2))
    },
    test("passing test") {
      assert(1, Predicate.equals(1))
    }
  )
})
