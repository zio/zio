package zio.test

import zio.test.Assertion._

object CompileSpec extends ZIOBaseSpec {

  def spec = suite("CompileSpec")(
    test("typeCheck must return Right if the specified string is valid Scala code") {
      assertZIO(typeCheck("1 + 1"))(isRight(anything))
    },
    test("typeCheck must return Left with an error message otherwise") {
      val expected = "value ++ is not a member of Int"
      if (TestVersion.isScala2) assertZIO(typeCheck("1 ++ 1"))(isLeft(equalTo(expected)))
      else assertZIO(typeCheck("1 ++ 1"))(isLeft(anything))
    }
  )
}
