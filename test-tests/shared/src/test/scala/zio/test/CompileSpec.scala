package zio.test

import zio.Has
import zio.clock.Clock
import zio.random.Random
import zio.test.Assertion._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }

object CompileSpec extends ZIOBaseSpec {

  def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ], TestFailure[Any], TestSuccess] = suite("CompileSpec")(
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
