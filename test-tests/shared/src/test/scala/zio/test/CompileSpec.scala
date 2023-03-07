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
    },
    test("typeCheck expansion must use Left and Right from scala.util") {
      // if this does not compile the macro expansion probably picks up the wrong Left or Right
      case class Left(s: Nothing)
      case class Right(s: Nothing)
      val assertRight = assertZIO(typeCheck("1 + 1"))(isRight(anything))
      val assertLeft  = assertZIO(typeCheck("1 ++ 1"))(isLeft(anything))
      (assertLeft <*> assertRight).map { case (l, r) => l && r }
    }
  )
}
