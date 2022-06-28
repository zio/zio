package zio.test

import zio.test.Assertion._

object CompileSpec212Plus extends ZIOBaseSpec {

  // doesn't work on 2.11 Native, any other combination works
  def spec: ZSpec[Environment, Failure] = suite("CompileSpec")(
    testM("typeCheck expansion must use Left and Right from scala.util") {
      // if this does not compile the macro expansion probably picks up the wrong Left or Right
      case class Left(s: Nothing)
      case class Right(s: Nothing)
      val assertRight = assertM(typeCheck("1 + 1"))(isRight(anything))
      val assertLeft  = assertM(typeCheck("1 ++ 1"))(isLeft(anything))
      (assertLeft <*> assertRight).map { case (l, r) => l && r }
    }
  )
}
