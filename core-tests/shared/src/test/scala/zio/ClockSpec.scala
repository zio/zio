package zio

import zio.test._
import zio.test.Assertion._

import java.time.Instant

object ClockSpec extends ZIOBaseSpec {

  val someFixedInstant = java.time.Instant.parse("2021-11-13T03:42:09.343835Z")
  val europeAmsterdamFixedClock =
    java.time.Clock.fixed(someFixedInstant, java.time.ZoneId.of("Europe/Amsterdam")) // 8 hours later than Phoenix
  val americaPhoenixFixedClock =
    java.time.Clock.fixed(someFixedInstant, java.time.ZoneId.of("America/Phoenix")) // 8 hours earlier than Amsterdam

  def spec = suite("ClockSpec")(
    test("currentDateTime does not throw a DateTimeException") {
      for {
        _ <- Clock.currentDateTime
      } yield assertCompletes
    },
    test("should output equal instant no matter what timezone") {
      val zioEuropeAmsterdamClock = Clock.ClockJava(europeAmsterdamFixedClock)
      val zioAmericaPhoenixClock  = Clock.ClockJava(americaPhoenixFixedClock)
      assertZIO(ZIO.collectAll(Chunk(zioEuropeAmsterdamClock.instant, zioAmericaPhoenixClock.instant)))(
        not(isDistinct)
      )
    },
    test("should output a offsetDateTime matching to timezone from java.time.Clock") {
      val zioEuropeAmsterdamClock = Clock.ClockJava(europeAmsterdamFixedClock)
      val zioAmericaPhoenixClock  = Clock.ClockJava(americaPhoenixFixedClock)
      assertZIO(ZIO.collectAll(Chunk(zioEuropeAmsterdamClock.currentDateTime, zioAmericaPhoenixClock.currentDateTime)))(
        isDistinct
      )
    },
    test("should output a localDateTime matching to timezone from java.time.Clock") {
      val zioEuropeAmsterdamClock = Clock.ClockJava(europeAmsterdamFixedClock)
      val zioAmericaPhoenixClock  = Clock.ClockJava(americaPhoenixFixedClock)
      assertZIO(ZIO.collectAll(Chunk(zioEuropeAmsterdamClock.localDateTime, zioAmericaPhoenixClock.localDateTime)))(
        isDistinct
      )
    },
    suite("TestClock")(
      test("should accept any Instant") {
        check(Gen.instant) { instant =>
          for {
            _   <- TestClock.setTime(instant)
            now <- Clock.instant
          } yield assertTrue(now == instant)
        }
      },
      test("should allow long sleep") {
        val maxMillisDuration = Duration.fromMillis(Long.MaxValue)

        for {
          _       <- TestClock.adjust(maxMillisDuration)
          _       <- TestClock.adjust(maxMillisDuration)
          promise <- Promise.make[Nothing, Unit]
          _       <- (ZIO.sleep(maxMillisDuration) *> promise.succeed(())).fork
          _       <- TestClock.setTime(Instant.MAX)
          _       <- promise.await
        } yield assertTrue(true)
      }
    )
  ) @@ TestAspect.exceptNative
}
