package zio.test.environment

import java.time.{ OffsetDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import zio._
import zio.clock._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment.TestClock._

object ClockSpec extends ZIOBaseSpec {

  def spec =
    suite("ClockSpec")(
      testM("sleep does not require passage of clock time") {
        for {
          ref    <- Ref.make(false)
          _      <- ref.set(true).delay(fromHours(10)).fork
          _      <- adjust(fromHours(11))
          result <- ref.get
        } yield assert(result)(isTrue)
      } @@ forked @@ nonFlaky,
      testM("sleep delays effect until time is adjusted") {
        for {
          ref    <- Ref.make(false)
          _      <- ref.set(true).delay(fromHours(10)).fork
          _      <- adjust(fromHours(9))
          result <- ref.get
        } yield assert(result)(isFalse)
      } @@ forked @@ nonFlaky,
      testM("sleep correctly handles multiple sleeps") {
        for {
          ref    <- Ref.make("")
          _      <- ref.update(_ + "World!").delay(fromHours(3)).fork
          _      <- ref.update(_ + "Hello, ").delay(fromHours(1)).fork
          _      <- adjust(fromHours(4))
          result <- ref.get
        } yield assert(result)(equalTo("Hello, World!"))
      } @@ forked @@ nonFlaky,
      testM("sleep correctly handles new set time") {
        for {
          ref    <- Ref.make(false)
          _      <- ref.set(true).delay(fromHours(10)).fork
          _      <- setTime(fromHours(11))
          result <- ref.get
        } yield assert(result)(isTrue)
      } @@ forked @@ nonFlaky,
      testM("adjust correctly advances nanotime") {
        for {
          time1 <- nanoTime
          _     <- adjust(fromMillis(1))
          time2 <- nanoTime
        } yield assert(fromNanos(time2 - time1))(equalTo(fromMillis(1)))
      },
      testM("adjust correctly advances currentTime") {
        for {
          time1 <- currentTime(TimeUnit.NANOSECONDS)
          _     <- adjust(fromMillis(1))
          time2 <- currentTime(TimeUnit.NANOSECONDS)
        } yield assert(time2 - time1)(equalTo(fromMillis(1).toNanos))
      },
      testM("adjust correctly advances currentDateTime") {
        for {
          time1 <- currentDateTime
          _     <- adjust(fromMillis(1))
          time2 <- currentDateTime
        } yield assert((time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli))(equalTo(1L))
      },
      testM("adjust does not produce sleeps") {
        for {
          _      <- adjust(fromMillis(1))
          sleeps <- sleeps
        } yield assert(sleeps)(isEmpty)
      },
      testM("setDateTime correctly sets currentDateTime") {
        for {
          expected <- UIO.effectTotal(OffsetDateTime.now(ZoneId.of("UTC+9")))
          _        <- setDateTime(expected)
          actual   <- clock.currentDateTime
        } yield assert(actual.toInstant.toEpochMilli)(equalTo(expected.toInstant.toEpochMilli))
      },
      testM("setTime correctly sets nanotime") {
        for {
          _    <- setTime(fromMillis(1))
          time <- clock.nanoTime
        } yield assert(time)(equalTo(fromMillis(1).toNanos))
      },
      testM("setTime correctly sets currentTime") {
        for {
          _    <- setTime(fromMillis(1))
          time <- currentTime(TimeUnit.NANOSECONDS)
        } yield assert(time)(equalTo(fromMillis(1).toNanos))
      },
      testM("setTime correctly sets currentDateTime") {
        for {
          _    <- TestClock.setTime(fromMillis(1))
          time <- currentDateTime
        } yield assert(time.toInstant.toEpochMilli)(equalTo(fromMillis(1).toMillis))
      },
      testM("setTime does not produce sleeps") {
        for {
          _      <- setTime(fromMillis(1))
          sleeps <- sleeps
        } yield assert(sleeps)(isEmpty)
      },
      testM("setTimeZone correctly sets timeZone") {
        setTimeZone(ZoneId.of("UTC+10")) *>
          assertM(timeZone)(equalTo(ZoneId.of("UTC+10")))
      },
      testM("setTimeZone does not produce sleeps") {
        setTimeZone(ZoneId.of("UTC+11")) *>
          assertM(sleeps)(isEmpty)
      },
      testM("timeout example from TestClock documentation works correctly") {
        val example = for {
          fiber  <- ZIO.sleep(fromMinutes(5)).timeout(fromMinutes(1)).fork
          _      <- TestClock.adjust(fromMinutes(1))
          result <- fiber.join
        } yield result == None
        assertM(example)(isTrue)
      } @@ forked @@ nonFlaky,
      testM("recurrence example from TestClock documentation works correctly") {
        val example = for {
          q <- Queue.unbounded[Unit]
          _ <- q.offer(()).delay(fromMinutes(60)).forever.fork
          a <- q.poll.map(_.isEmpty)
          _ <- TestClock.adjust(fromMinutes(60))
          b <- q.take.as(true)
          c <- q.poll.map(_.isEmpty)
          _ <- TestClock.adjust(fromMinutes(60))
          d <- q.take.as(true)
          e <- q.poll.map(_.isEmpty)
        } yield a && b && c && d && e
        assertM(example)(isTrue)
      } @@ forked @@ nonFlaky,
      testM("clock time is always 0 at the start of a test that repeats")(
        for {
          clockTime <- currentTime(TimeUnit.NANOSECONDS)
          _         <- sleep(fromNanos(2)).fork
          _         <- adjust(fromNanos(3))
        } yield assert(clockTime)(equalTo(fromMillis(0).toNanos))
      ) @@ forked @@ nonFlaky(3),
      testM("TestClock interacts correctly with Scheduled.fixed") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          ref       <- Ref.make(3)
          countdown = ref.updateAndGet(_ - 1).flatMap(n => latch.succeed(()).when(n == 0))
          _         <- countdown.repeat(Schedule.fixed(fromSeconds(2))).delay(fromSeconds(1)).fork
          _         <- TestClock.adjust(fromSeconds(5))
          _         <- latch.await
        } yield assertCompletes
      },
      testM("adjustments to time are visible on other fibers") {
        for {
          promise <- Promise.make[Nothing, Unit]
          effect  = adjust(fromSeconds(1)) *> clock.currentTime(TimeUnit.SECONDS)
          result  <- (effect <* promise.succeed(())) <&> (promise.await *> effect)
        } yield assert(result)(equalTo((1L, 2L)))
      }
    )
}
