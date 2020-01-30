package zio.test

import zio.ZIO
import zio.clock._
import zio.test.Assertion._
import zio.test.TestAspect.failure
import zio.test.TestUtils.execute

object TestSpec extends ZIOBaseSpec {

  def spec = suite("TestSpec")(
    testM("assertM works correctly") {
      assertM(nanoTime)(equalTo(0L))
    },
    testM("testM error is test failure") {
      for {
        _      <- ZIO.failNow("fail")
        result <- ZIO.succeedNow("succeed")
      } yield assert(result)(equalTo("succeed"))
    } @@ failure,
    testM("testM is polymorphic in error type") {
      for {
        _      <- ZIO.effect(())
        result <- ZIO.succeedNow("succeed")
      } yield assert(result)(equalTo("succeed"))
    },
    testM("testM suspends effects") {
      var n = 0
      val spec = suite("suite")(
        testM("test1") {
          n += 1
          ZIO.succeedNow(assertCompletes)
        },
        testM("test2") {
          n += 1
          ZIO.succeedNow(assertCompletes)
        }
      ).filterLabels(_.render == "test2").get
      for {
        _ <- execute(spec)
      } yield assert(n)(equalTo(1))
    }
  )
}
