package zio.test.environment

import java.time.ZoneId
import java.util.concurrent.TimeUnit

import zio._
import zio.clock._
import zio.duration.Duration._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment.TestClock._

object ClockSpec
    extends ZIOBaseSpec(
      suite("ClockSpec")(
        testM("sleep does not require passage of clock time") {
          for {
            latch <- Promise.make[Nothing, Unit]
            _     <- (sleep(10.hours) *> latch.succeed(())).fork
            _     <- adjust(11.hours)
            _     <- latch.await
          } yield assert((), anything)
        } @@ nonFlaky(100),
        testM("sleep delays effect until time is adjusted") {
          for {
            ref    <- Ref.make(true)
            _      <- (sleep(10.hours) *> ref.set(false)).fork
            _      <- adjust(9.hours)
            result <- ref.get
          } yield assert(result, isTrue)
        } @@ nonFlaky(100),
        //todo  remove `@@ after(setTime(0.hours))` if possible. This doesn't depend on absolute time so I don't think
        // it should rely on resetting the clock. Fails intermittently without it.
        testM("sleep correctly handles multiple sleeps") {
          for {
            latch1 <- Promise.make[Nothing, Unit]
            latch2 <- Promise.make[Nothing, Unit]
            ref    <- Ref.make("")
            _      <- (sleep(3.hours) *> ref.update(_ + "World!") *> latch2.succeed(())).fork
            _      <- (sleep(1.hours) *> ref.update(_ + "Hello, ") *> latch1.succeed(())).fork
            _      <- adjust(2.hours)
            _      <- latch1.await
            _      <- adjust(2.hours)
            _      <- latch2.await
            result <- ref.get
          } yield assert(result, equalTo("Hello, World!"))
        } @@ after(setTime(0.hours))
          @@ nonFlaky(100),
        testM("sleep correctly handles new set time") {
          for {
            latch <- Promise.make[Nothing, Unit]
            _     <- (sleep(10.hours) *> latch.succeed(())).fork
            _     <- setTime(11.hours)
            _     <- latch.await
          } yield assert((), anything)
        } @@ nonFlaky(100)
          @@ timeout(10.seconds),
        //todo fix - times out
        testM("sleep does sleep instantly when sleep duration less than set time") {
          for {
            latch <- Promise.make[Nothing, Unit]
            _     <- (setTime(11.hours) *> latch.succeed(())).fork
            _     <- latch.await *> sleep(10.hours)
          } yield assert((), anything)
        } @@ after(setTime(0.hours))
          @@ timeout(1.seconds) //todo remove once working
          @@ nonFlaky(100),
        testM("adjust correctly advances nanotime") {
          for {
            time1 <- nanoTime
            _     <- adjust(1.millis)
            time2 <- nanoTime
          } yield assert(fromNanos(time2 - time1), equalTo(1.millis))
        },
        testM("adjust correctly advances currentTime") {
          for {
            time1 <- currentTime(TimeUnit.NANOSECONDS)
            _     <- adjust(1.millis)
            time2 <- currentTime(TimeUnit.NANOSECONDS)
          } yield assert(time2 - time1, equalTo(1.millis.toNanos))
        },
        testM("adjust correctly advances currentDateTime") {
          for {
            time1 <- currentDateTime
            _     <- adjust(1.millis)
            time2 <- currentDateTime
          } yield assert((time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli), equalTo(1L))
        },
        testM("adjust does not produce sleeps") {
          adjust(1.millis) *> assertM(sleeps, isEmpty)
        },
        testM("setTime correctly sets nanotime") {
          for {
            _    <- setTime(1.millis)
            time <- clock.nanoTime
          } yield assert(time, equalTo(1.millis.toNanos))
        },
        testM("setTime correctly sets currentTime") {
          for {
            _    <- setTime(1.millis)
            time <- currentTime(TimeUnit.NANOSECONDS)
          } yield assert(time, equalTo(1.millis.toNanos))
        },
        testM("setTime correctly sets currentDateTime") {
          for {
            _    <- TestClock.setTime(1.millis)
            time <- currentDateTime
          } yield assert(time.toInstant.toEpochMilli, equalTo(1.millis.toMillis))
        },
        testM("setTime does not produce sleeps") {
          for {
            _      <- setTime(1.millis)
            sleeps <- sleeps
          } yield assert(sleeps, isEmpty)
        },
        testM("setTimeZone correctly sets timeZone") {
          setTimeZone(ZoneId.of("UTC+10")) *>
            assertM(timeZone, equalTo(ZoneId.of("UTC+10")))
        },
        testM("setTimeZone does not produce sleeps") {
          setTimeZone(ZoneId.of("UTC+11")) *>
            assertM(sleeps, isEmpty)
        },
        testM("timeout example from TestClock documentation works correctly") {
          val example = for {
            fiber  <- ZIO.sleep(5.minutes).timeout(1.minute).fork
            _      <- TestClock.adjust(1.minute)
            result <- fiber.join
          } yield result == None
          assertM(example, isTrue)
        } @@ nonFlaky(100),
        testM("recurrence example from TestClock documentation works correctly") {
          val example = for {
            q <- Queue.unbounded[Unit]
            _ <- (q.offer(()).delay(60.minutes)).forever.fork
            a <- q.poll.map(_.isEmpty)
            _ <- TestClock.adjust(60.minutes)
            b <- q.take.as(true)
            c <- q.poll.map(_.isEmpty)
            _ <- TestClock.adjust(60.minutes)
            d <- q.take.as(true)
            e <- q.poll.map(_.isEmpty)
          } yield a && b && c && d && e
          assertM(example, isTrue)
        },
        //todo fix - looks like fiberTime is 100x larger than it's supposed to be - conversion error somewhere?
        testM("fiber time is not subject to race conditions") {
          for {
            _      <- setTime(Duration.Infinity)
            _      <- sleep(2.millis).zipPar(sleep(1.millis))
            result <- fiberTime
          } yield assert(result.toMillis, equalTo(2.millis.toMillis)) //todo fails "200 did not satisfy equalTo(2)"
        } @@ nonFlaky(100)
      )
    )
