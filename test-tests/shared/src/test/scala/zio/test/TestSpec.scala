package zio.test

import zio.ZIO
import zio.clock._
import zio.random._
import zio.test.Assertion._
import zio.test.TestAspect.failure
import zio.test.TestUtils.execute

object TestSpec
    extends ZIOBaseSpec(
      suite("TestSpec")(
        testM("assertM works correctly") {
          assertM(nanoTime, equalTo(0L))
        },
        testM("testM error is test failure") {
          for {
            n      <- nextInt
            _      <- ZIO.effect(n / 0)
            result <- ZIO.succeed(0)
          } yield assert(result, equalTo(0))
        } @@ failure,
        testM("testM is polymorphic in error type") {
          for {
            _      <- ZIO.effect(())
            result <- ZIO.succeed("succeed")
          } yield assert(result, equalTo("succeed"))
        },
        testM("testM suspends effects") {
          var n = 0
          val spec = suite("suite")(
            testM("test1") {
              n += 1
              ZIO.succeed(assertCompletes)
            },
            testM("test2") {
              n += 1
              ZIO.succeed(assertCompletes)
            }
          ).filterLabels(_ == "test2").get
          for {
            _ <- execute(spec)
          } yield assert(n, equalTo(1))
        }
      )
    )
