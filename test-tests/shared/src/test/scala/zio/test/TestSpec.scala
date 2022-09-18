package zio.test

import zio._
import zio.Clock._
import zio.internal.macros.StringUtils.StringOps
import zio.stm.STM
import zio.test.Assertion._
import zio.test.TestAspect.{failing, timeout}
import zio.test.TestUtils.execute

object TestSpec extends ZIOBaseSpec {

  def spec = suite("TestSpec")(
    test("assertZIO works correctly") {
      assertZIO(nanoTime)(equalTo(0L))
    },
    test("test error is test failure") {
      for {
        _      <- ZIO.fail("fail")
        result <- ZIO.succeed("succeed")
      } yield assert(result)(equalTo("succeed"))
    } @@ failing,
    test("test is polymorphic in error type") {
      for {
        _      <- ZIO.attempt(())
        result <- ZIO.succeed("succeed")
      } yield assert(result)(equalTo("succeed"))
    },
    test("test suspends effects") {
      var n = 0
      val spec = suite("suite")(
        test("test1") {
          n += 1
          ZIO.succeed(assertCompletes)
        },
        test("test2") {
          n += 1
          ZIO.succeed(assertCompletes)
        }
      ).filterLabels(_ == "test2").get
      for {
        _ <- execute(spec)
      } yield assert(n)(equalTo(1))
    },
    test("scoped effects can be tested") {
      for {
        ref   <- Ref.make(false)
        _     <- ZIO.acquireRelease(ref.set(true))(_ => ref.set(false))
        value <- ref.get
      } yield assert(value)(isTrue)
    },
    test("transactional effects can be tested") {
      for {
        message <- STM.succeed("Hello from an STM transaction!")
      } yield assert(message)(anything)
    },
    test("fail-fast assertion by automatic lifting to ZIO") {
      for {
        ref <- Ref.make(0)
        spec = test("test") {
                 for {
                   _ <- ref.update(_ + 1)
                   _ <- assertTrue(1 == 2)
                   _ <- ref.update(_ + 1)
                 } yield assertTrue(3 == 4)
               }
        summary <- execute(spec)
        count   <- ref.get
      } yield assert(count)(equalTo(1)) &&
        assert(summary.fail)(equalTo(1)) &&
        assert(summary.failureDetails.unstyled)(
          containsString("1 == 2") &&
            not(containsString("3 == 4"))
        )
    },
    test("composed assertions are lifted to ZIO and fully evaluated") {
      for {
        ref <- Ref.make(0)
        spec = test("test") {
                 for {
                   _ <- ref.update(_ + 1)
                   _ <- assertTrue(1 == 2) && assertTrue(3 == 4)
                   _ <- ref.update(_ + 1)
                 } yield assertCompletes
               }
        summary <- execute(spec)
        count   <- ref.get
      } yield assert(count)(equalTo(1)) &&
        assert(summary.fail)(equalTo(1)) &&
        assert(summary.failureDetails.unstyled)(
          containsString("1 == 2") &&
            containsString("3 == 4")
        )
    },
    suite("suites can be effectual") {
      ZIO.succeed {
        Chunk(
          test("a test in an effectual suite")(assertCompletes),
          test("another test in an effectual suite")(assertCompletes)
        )
      }
    }
  )
}
