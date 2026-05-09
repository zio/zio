
package zio

import zio.test._
import zio.test.Assertion._
import zio.durationInt._

object TimeoutTOSpec extends ZIOSpecDefault {
  def spec = suite("TimeoutTOSpec")(
    test("timeoutTO returns Some if the effect completes within the timeout") {
      for {
        result <- ZIO.succeed(42).timeoutTO(1.second)
      } yield assert(result)(isSome(equalTo(42)))
    },
    test("timeoutTO returns None if the effect does not complete within the timeout") {
      for {
        result <- ZIO.sleep(2.seconds).timeoutTO(1.second)
      } yield assert(result)(isNone)
    },
    test("timeoutTO with a zero duration returns immediately") {
      for {
        start <- Clock.currentTime(TimeUnit.MILLISECONDS)
        result <- ZIO.sleep(1.second).timeoutTO(0.seconds)
        end <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield assert(result)(isNone) && assert(end - start)(isLessThan(100L))
    },
    test("timeoutTO with an infinite duration never times out") {
      for {
        result <- ZIO.succeed(42).timeoutTO(Duration.Infinity)
      } yield assert(result)(isSome(equalTo(42)))
    },
    test("timeoutTO with a negative duration returns immediately") {
      for {
        start <- Clock.currentTime(TimeUnit.MILLISECONDS)
        result <- ZIO.sleep(1.second).timeoutTO(-1.seconds)
        end <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield assert(result)(isNone) && assert(end - start)(isLessThan(100L))
    },
    test("timeoutTO properly handles interruption") {
      for {
        ref <- Ref.make(false)
        fiber <- ZIO.never.onInterrupt(ref.set(true)).timeoutTO(1.second).fork
        _ <- TestClock.adjust(2.seconds)
        _ <- fiber.join
        interrupted <- ref.get
      } yield assert(interrupted)(isTrue)
    },
    test("timeoutTO cancels the scheduled task when the effect completes") {
      for {
        ref <- Ref.make(0)
        _ <- ZIO.succeed(42).onExit(_ => ref.update(_ + 1)).timeoutTO(1.second)
        count <- ref.get
      } yield assert(count)(equalTo(1))
    },
    test("timeoutTO cancels the scheduled task when the effect fails") {
      for {
        ref <- Ref.make(0)
        _ <- ZIO.fail("error").onExit(_ => ref.update(_ + 1)).timeoutTO(1.second).either
        count <- ref.get
      } yield assert(count)(equalTo(1))
    },
    test("timeoutTO with a very short timeout") {
      for {
        result <- ZIO.sleep(10.millis).timeoutTO(1.millis)
      } yield assert(result)(isNone)
    },
    test("timeoutTO with a very short effect") {
      for {
        result <- ZIO.unit.timeoutTO(1.second)
      } yield assert(result)(isSome(isUnit))
    }
  )
}
