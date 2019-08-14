package zio.examples.test

import zio.test.{ assert, suite, test, DefaultRunnableSpec, Predicate }

object ExampleSpec
    extends DefaultRunnableSpec(
      suite("some suite")(
        test("failing test") {
          assert(1, Predicate.equals(2))
        },
        test("passing test") {
          assert(1, Predicate.equals(1))
        }
      )
    )
