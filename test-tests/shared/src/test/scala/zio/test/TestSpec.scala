package zio.test

import zio._
import zio.Clock._
import zio.test.environment._
import zio.test.Assertion._
import zio.test.TestAspect.{failing, timeout}
import zio.test.TestUtils.execute

object TestSpec extends ZIOBaseSpec {

  override val runner: TestRunner[TestEnvironment, Any] =
    defaultTestRunner.withRuntimeConfig { runtimeConfig =>
      runtimeConfig.copy(logger = runtimeConfig.logger.filterLogLevel(_ >= LogLevel.Error))
    }

  def spec: Spec[Environment, TestFailure[Any], TestSuccess] = suite("TestSpec")(
    test("assertM works correctly") {
      assertM(nanoTime)(equalTo(0L))
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
    test("test does not wait to interrupt children") {
      for {
        promise <- Promise.make[Nothing, Unit]
        _       <- (promise.succeed(()) *> Live.live(ZIO.sleep(20.seconds))).uninterruptible.fork
        _       <- promise.await
      } yield assertCompletes
    } @@ timeout(10.seconds)
  )
}
