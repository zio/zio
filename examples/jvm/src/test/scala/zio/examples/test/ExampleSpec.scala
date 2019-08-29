package zio.examples.test

import zio.test._
import zio.test.Assertion._

object ExampleSpec
    extends DefaultRunnableSpec(
      suite("some suite")(
        test("failing test") {
          assert(1, equalTo(2))
        },
        test("passing test") {
          assert(1, equalTo(1))
        }
      )
    )
