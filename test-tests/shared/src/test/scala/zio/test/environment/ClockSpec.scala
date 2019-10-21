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
            _     <- sleep(10.hours).flatMap(_ => latch.succeed(())).fork
            _     <- adjust(11.hours)
            _     <- latch.await
          } yield assert((), anything)
        } @@ nonFlaky(100)
          @@ timeout(10.second),
        testM("sleep delays effect until time is adjusted") { //flaky
          for {
            ref    <- Ref.make(true)
            _      <- sleep(10.hours).flatMap(_ => ref.set(false)).fork
            _      <- adjust(9.hours)
            result <- ref.get
          } yield assert(result, isTrue)
        } @@ nonFlaky(100)
          @@ timeout(10.second),
        testM("sleep correctly handles multiple sleeps") { //flaky
          for {
            latch1 <- Promise.make[Nothing, Unit]
            latch2 <- Promise.make[Nothing, Unit]
            ref    <- Ref.make("")
            _      <- sleep(3.hours).flatMap(_ => ref.update(_ + "World!")).flatMap(_ => latch2.succeed(())).fork
            _      <- sleep(1.hours).flatMap(_ => ref.update(_ + "Hello, ")).flatMap(_ => latch1.succeed(())).fork
            _      <- adjust(2.hours)
            _      <- latch1.await
            _      <- adjust(2.hours)
            _      <- latch2.await
            result <- ref.get
          } yield assert(result, equalTo("Hello, World!"))
        } @@ nonFlaky(100)
          @@ timeout(10.second),
        testM("sleep correctly handles new set time") {
          for {
            latch <- Promise.make[Nothing, Unit]
            _     <- sleep(10.hours).flatMap(_ => latch.succeed(())).fork
            _     <- setTime(11.hours)
            _     <- latch.await
          } yield assert((), anything)
        } @@ nonFlaky(100)
          @@ timeout(10.second),
        testM("sleep does sleep instantly when sleep duration less than set time") { //flaky
          for {
            latch <- Promise.make[Nothing, Unit]
            _     <- (setTime(11.hours) *> latch.succeed(())).fork
            _     <- latch.await *> sleep(10.hours)
          } yield assert((), anything)
        } @@ nonFlaky(100)
          @@ timeout(10.seconds),
        testM("adjust correctly advances nanotime") {
          for {
            time1 <- nanoTime
            _     <- adjust(1.millis)
            time2 <- nanoTime
          } yield assert(fromNanos(time2 - time1), equalTo(1.millis))
        }
          @@ timeout(10.second),
        testM("adjust correctly advances currentTime") {
          for {
            time1 <- currentTime(TimeUnit.NANOSECONDS)
            _     <- adjust(1.millis)
            time2 <- currentTime(TimeUnit.NANOSECONDS)
          } yield assert(fromNanos(time2 - time1), equalTo(1.millis))
        },
        testM("adjust correctly advances currentDateTime") {
          for {
            time1 <- currentDateTime
            _     <- adjust(1.millis)
            time2 <- currentDateTime
          } yield assert((time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli), equalTo(1L))
        },
        testM("adjust does not produce sleeps") {
          for {
            _      <- adjust(1.millis)
            sleeps <- sleeps
          } yield assert(sleeps, isEmpty)
        },
        testM("setTime correctly sets nanotime") {
          for {
            _    <- setTime(1.millis)
            time <- clock.nanoTime
          } yield assert(fromNanos(time), equalTo(1.millis))
        },
        testM("setTime correctly sets currentTime") {
          for {
            _    <- setTime(1.millis)
            time <- currentTime(TimeUnit.NANOSECONDS)
          } yield assert(fromNanos(time), equalTo(1.millis))
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
          for {
            _        <- setTimeZone(ZoneId.of("UTC"))
            timeZone <- timeZone
          } yield assert(timeZone, equalTo(ZoneId.of("UTC")))
        },
        testM("setTimeZone does not produce sleeps") {
          setTimeZone(ZoneId.of("UTC"))
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
        testM("recurrence example from TestClock documentation works correctly") { //flaky
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
        } @@ nonFlaky(100),
        testM("fiber time is not subject to race conditions") { //flaky
          for {
            _      <- adjust(Duration.Infinity)
            _      <- sleep(2.millis).zipPar(ZIO.sleep(1.millis))
            result <- TestClock.fiberTime
          } yield assert(result, equalTo(2.millis))
        } @@ nonFlaky(100)
      )
    )
