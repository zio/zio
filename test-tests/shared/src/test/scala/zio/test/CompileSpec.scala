package zio.test

import zio.test.Assertion._

object CompileSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("CompileSpec")(
    testM("typeCheck must return Right if the specified string is valid Scala code") {
      assertM(typeCheck("1 + 1"))(isRight(anything))
    },
    testM("typeCheck must return Left with an error message otherwise") {
      val expected = "value ++ is not a member of Int"
      if (TestVersion.isScala2) assertM(typeCheck("1 ++ 1"))(isLeft(equalTo(expected)))
      else assertM(typeCheck("1 ++ 1"))(isLeft(anything))
    }
  )
}
