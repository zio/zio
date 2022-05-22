package zio.test

import zio.test.Assertion.isNone

object TestTraceSpec extends ZIOBaseSpec {

  def spec = suite("TestTraceSpec")(
    test("TestTrace is stack safe") {
      val testTraceAnd = List
        .fill(10000)(TestTrace.succeed(true))
        .fold(TestTrace.succeed(true))(TestTrace.And)
      val testTraceOr = TestTrace.Or(
        List
          .fill(10000)(TestTrace.succeed(false))
          .fold(TestTrace.succeed(false))(TestTrace.Or),
        TestTrace.succeed(true)
      )
      val testTraceAndThen = TestTrace.AndThen(testTraceAnd, testTraceOr)

      val result = TestTrace.prune(testTraceAndThen, negated = false)

      assert(result)(isNone)
    }
  )

}
