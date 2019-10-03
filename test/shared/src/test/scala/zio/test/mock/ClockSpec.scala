package zio.test.mock

import java.util.concurrent.TimeUnit
import java.time.ZoneId

import zio._
import zio.duration._
import zio.test.Async
import zio.test.mock.MockClock.DefaultData
import zio.test.TestUtils.{ label, nonFlaky }
import zio.test.ZIOBaseSpec

object ClockSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(e1, "sleep does not require passage of clock time"),
    label(e2, "sleep delays effect until time is adjusted"),
    label(e3, "sleep correctly handles multiple sleeps"),
    label(e4, "sleep correctly handles new set time"),
    label(e5, "sleep does sleep instanly when sleep duration less than set time"),
    label(e6, "adjust correctly advances nanotime"),
    label(e7, "adjust correctly advances currentTime"),
    label(e8, "adjust correctly advances currentDateTime"),
    label(e9, "adjust does not produce sleeps "),
    label(e10, "setTime correctly sets nanotime"),
    label(e11, "setTime correctly sets currentTime"),
    label(e12, "setTime correctly sets currentDateTime"),
    label(e13, "setTime does not produce sleeps "),
    label(e14, "setTimeZone correctly sets timeZone"),
    label(e15, "setTimeZone does not produce sleeps "),
    label(e16, "timeout example from documentation works correctly"),
    label(e17, "recurrence example from documentation works correctly"),
    label(e18, "fiber time is not subject to race conditions")
  )

  def e1 =
    unsafeRunToFuture {
      nonFlaky {
        for {
          mockClock <- MockClock.makeMock(DefaultData)
          latch     <- Promise.make[Nothing, Unit]
          _         <- mockClock.sleep(10.hours).flatMap(_ => latch.succeed(())).fork
          _         <- mockClock.adjust(11.hours)
          _         <- latch.await
        } yield true
      }
    }

  def e2 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        ref       <- Ref.make(true)
        _         <- mockClock.sleep(10.hours).flatMap(_ => ref.set(false)).fork
        _         <- mockClock.adjust(9.hours)
        result    <- ref.get
      } yield result
    )

  def e3 =
    unsafeRunToFuture {
      nonFlaky {
        for {
          mockClock <- MockClock.makeMock(DefaultData)
          latch1    <- Promise.make[Nothing, Unit]
          latch2    <- Promise.make[Nothing, Unit]
          ref       <- Ref.make("")
          _         <- mockClock.sleep(3.hours).flatMap(_ => ref.update(_ + "World!")).flatMap(_ => latch2.succeed(())).fork
          _         <- mockClock.sleep(1.hours).flatMap(_ => ref.update(_ + "Hello, ")).flatMap(_ => latch1.succeed(())).fork
          _         <- mockClock.adjust(2.hours)
          _         <- latch1.await
          _         <- mockClock.adjust(2.hours)
          _         <- latch2.await
          result    <- ref.get
        } yield result == "Hello, World!"
      }
    }

  def e4 =
    unsafeRunToFuture {
      nonFlaky {
        for {
          mockClock <- MockClock.makeMock(DefaultData)
          latch     <- Promise.make[Nothing, Unit]
          _         <- mockClock.sleep(10.hours).flatMap(_ => latch.succeed(())).fork
          _         <- mockClock.setTime(11.hours)
          _         <- latch.await
        } yield true
      }
    }

  def e5 =
    unsafeRunToFuture {
      nonFlaky {
        for {
          mockClock <- MockClock.makeMock(DefaultData)
          latch     <- Promise.make[Nothing, Unit]
          _         <- (mockClock.setTime(11.hours) *> latch.succeed(())).fork
          _         <- latch.await *> mockClock.sleep(10.hours)
        } yield true
      }
    }

  def e6 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.nanoTime
        _         <- mockClock.adjust(1.millis)
        time2     <- mockClock.nanoTime
      } yield (time2 - time1) == 1000000L
    )

  def e7 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- mockClock.adjust(1.millis)
        time2     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) == 1L
    )

  def e8 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentDateTime
        _         <- mockClock.adjust(1.millis)
        time2     <- mockClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) == 1L
    )

  def e9 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.adjust(1.millis)
        sleeps    <- mockClock.sleeps
      } yield sleeps == Nil
    )

  def e10 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        time      <- mockClock.nanoTime
      } yield time == 1000000L
    )

  def e11 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        time      <- mockClock.currentTime(TimeUnit.MILLISECONDS)
      } yield time == 1L
    )

  def e12 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        time      <- mockClock.currentDateTime
      } yield time.toInstant.toEpochMilli == 1L
    )

  def e13 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        sleeps    <- mockClock.sleeps
      } yield sleeps == Nil
    )

  def e14 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTimeZone(ZoneId.of("UTC"))
        timeZone  <- mockClock.timeZone
      } yield timeZone == ZoneId.of("UTC")
    )

  def e15 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTimeZone(ZoneId.of("UTC"))
        sleeps    <- mockClock.sleeps
      } yield sleeps == Nil
    )

  def e16 =
    unsafeRunToFuture {
      nonFlaky {
        val io = for {
          fiber  <- ZIO.sleep(5.minutes).timeout(1.minute).fork
          _      <- MockClock.adjust(1.minute)
          result <- fiber.join
        } yield result == None
        io.provideM(MockClock.make(MockClock.DefaultData))
      }
    }

  def e17 =
    unsafeRunToFuture {
      nonFlaky {
        val io = for {
          q <- Queue.unbounded[Unit]
          _ <- (q.offer(()).delay(60.minutes)).forever.fork
          a <- q.poll.map(_.isEmpty)
          _ <- MockClock.adjust(60.minutes)
          b <- q.take.as(true)
          c <- q.poll.map(_.isEmpty)
          _ <- MockClock.adjust(60.minutes)
          d <- q.take.as(true)
          e <- q.poll.map(_.isEmpty)
        } yield a && b && c && d && e
        io.provideM(MockClock.make(MockClock.DefaultData))
      }
    }

  def e18 =
    unsafeRunToFuture {
      nonFlaky {
        for {
          mockClock <- MockClock.makeMock(DefaultData)
          fiber     <- mockClock.sleep(2.millis).zipPar(mockClock.sleep(1.millis)).fork
          _         <- mockClock.adjust(2.millis)
          _         <- fiber.join
          result    <- mockClock.fiberTime
        } yield result.toNanos == 2000000L
      }
    }
}
