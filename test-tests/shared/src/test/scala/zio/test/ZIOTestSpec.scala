package zio.test

object ZIOTestSpec
    extends DefaultRunnableSpec(
      suite("ZIOTestSpec")(
        test("ZIO Test can be used to test itself") {
          assert(true, Assertion.isTrue)
        }
      )
    )
