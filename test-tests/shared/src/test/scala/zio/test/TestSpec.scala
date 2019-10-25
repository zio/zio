package zio.test

import zio.ZIO
import zio.clock._
import zio.test.Assertion._
import zio.test.TestAspect.failure

object TestSpec
    extends ZIOBaseSpec(
      suite("TestSpec")(
        testM("assertM works correctly") {
          assertM(nanoTime, equalTo(0L))
        },
        testM("testM error is test failure") {
          for {
            _      <- ZIO.fail("fail")
            result <- ZIO.succeed("succeed")
          } yield assert(result, equalTo("succeed"))
        } @@ failure,
        testM("testM is polymorphic in error type") {
          for {
            _      <- ZIO.effect(())
            result <- ZIO.succeed("succeed")
          } yield assert(result, equalTo("succeed"))
        }
      )
    )
