package zio

import zio.test.Assertion._
import zio.test._

object PromiseBecomeSpec extends ZIOBaseSpec {

  def spec: Spec[Any, TestFailure[Any]] = suite("Promise.become")(
    test("completes promise with fiber success") {
      for {
        promise <- Promise.make[Nothing, Int]
        fiber   <- ZIO.succeed(42).fork
        _       <- promise.become(fiber)
        value   <- promise.await
      } yield assert(value)(equalTo(42))
    },
    test("completes promise with fiber failure") {
      for {
        promise <- Promise.make[String, Int]
        fiber   <- ZIO.fail("error").fork
        _       <- promise.become(fiber)
        result  <- promise.await.exit
      } yield assert(result)(fails(equalTo("error")))
    },
    test("completes promise with fiber interruption") {
      for {
        promise <- Promise.make[String, Int]
        latch   <- Promise.make[Nothing, Unit]
        fiber   <- (latch.succeed(()) *> ZIO.never).fork
        _       <- latch.await
        _       <- fiber.interrupt
        _       <- promise.become(fiber)
        result  <- promise.await.exit
      } yield assert(result)(isInterrupted)
    },
    test("returns false if promise already completed") {
      for {
        promise <- Promise.make[Nothing, Int]
        fiber   <- ZIO.succeed(42).fork
        _       <- promise.succeed(1)
        result  <- promise.become(fiber)
        value   <- promise.await
      } yield assert(result)(isFalse) && assert(value)(equalTo(1))
    },
    test("completes promise when fiber already completed") {
      for {
        promise <- Promise.make[Nothing, Int]
        fiber   <- ZIO.succeed(42).fork
        _       <- fiber.await
        result  <- promise.become(fiber)
        value   <- promise.await
      } yield assert(result)(isTrue) && assert(value)(equalTo(42))
    },
    test("efficiently chains multiple promises to same fiber") {
      for {
        promise1 <- Promise.make[Nothing, Int]
        promise2 <- Promise.make[Nothing, Int]
        promise3 <- Promise.make[Nothing, Int]
        fiber    <- ZIO.succeed(42).delay(10.millis).fork
        _        <- promise1.become(fiber)
        _        <- promise2.become(fiber)
        _        <- promise3.become(fiber)
        v1       <- promise1.await
        v2       <- promise2.await
        v3       <- promise3.await
      } yield assert(v1)(equalTo(42)) && assert(v2)(equalTo(42)) && assert(v3)(equalTo(42))
    },
    test("works with synthetic fibers") {
      for {
        promise <- Promise.make[Nothing, Int]
        fiber1  <- ZIO.succeed(21).fork
        fiber2  <- ZIO.succeed(21).fork
        fiber   = fiber1.zipWith(fiber2)(_ + _)
        _       <- promise.become(fiber)
        value   <- promise.await
      } yield assert(value)(equalTo(42))
    },
    test("can be used to avoid promise await overhead") {
      // This test verifies that become can be used in fork/join scenarios
      for {
        promise <- Promise.make[Nothing, Int]
        fiber   <- ZIO.succeed(1).repeatN(100).fork
        _       <- promise.become(fiber)
        value   <- promise.await
      } yield assert(value)(equalTo(1))
    }
  )
}
