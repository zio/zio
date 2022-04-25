package zio.test

import zio.test.Assertion._

object OopsSpec extends ZIOBaseSpec {

  val failing = TestAspect.failing

  def spec: Spec[Annotations, TestFailure[Any]] =
    suite("AssertionSpec")(
      test("assertion") {
        assert(10)(not(equalTo(10)))
      } @@ failing
    )

}
