package zio.test

import zio.test.Assertion.equalTo

object TestArrowSpec extends ZIOBaseSpec {

  def spec = suite("TestArrowSpec")(
    test("TestArrow is stack safe") {
      val testArrowsAnd = List
        .fill(10000)(TestArrow.succeed(true))
        .fold(TestArrow.succeed(true))(TestArrow.And(_, _))
      val testArrowsOr = TestArrow.Or(
        List
          .fill(10000)(TestArrow.succeed(false))
          .fold(TestArrow.succeed(false))(TestArrow.Or(_, _)),
        TestArrow.succeed(true)
      )
      val testArrowAndThen = TestArrow.AndThen(testArrowsAnd, testArrowsOr)

      val result = TestArrow.run(testArrowAndThen, Right(true))

      assert(result.result)(equalTo(Result.Succeed(true)))
    }
  )

}
