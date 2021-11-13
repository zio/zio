package zio.clock

import zio._
import zio.test._
import zio.test.Assertion._

import java.time.DateTimeException

object ClockSpec extends ZIOBaseSpec {

  val someFixedInstant = java.time.Instant.parse("2021-11-13T03:42:09.343835Z")
  val europeAmsterdamFixedClock =
    java.time.Clock.fixed(someFixedInstant, java.time.ZoneId.of("Europe/Amsterdam")) // 8 hours later than Phoenix
  val americaPhoenixFixedClock =
    java.time.Clock.fixed(someFixedInstant, java.time.ZoneId.of("America/Phoenix")) // 8 hours earlier than Amsterdam

  def spec: Spec[Clock, TestFailure[DateTimeException], TestSuccess] = suite("ClockSpec")(
    testM("currentDateTime does not throw a DateTimeException") {
      for {
        _ <- clock.currentDateTime
      } yield assertCompletes
    },
    testM("should output equal instant no matter what timezone") {
      val zioEuropeAmsterdamClock: Clock.Service = Clock.Service.javaClock(europeAmsterdamFixedClock)
      val zioAmericaPhoenixClock: Clock.Service  = Clock.Service.javaClock(americaPhoenixFixedClock)
      assertM(
        ZIO.mapN(zioEuropeAmsterdamClock.instant, zioAmericaPhoenixClock.instant) { case (amsterdam, phoenix) =>
          amsterdam :: phoenix :: Nil
        }
      )(not(isDistinct))
    },
    testM("should output a offsetDateTime matching to timezone from java.time.Clock") {
      val zioEuropeAmsterdamClock: Clock.Service = Clock.Service.javaClock(europeAmsterdamFixedClock)
      val zioAmericaPhoenixClock: Clock.Service  = Clock.Service.javaClock(americaPhoenixFixedClock)
      assertM(
        ZIO.mapN(zioEuropeAmsterdamClock.currentDateTime, zioAmericaPhoenixClock.currentDateTime) {
          case (amsterdam, phoenix) =>
            amsterdam :: phoenix :: Nil
        }
      )(isDistinct)
    },
    testM("should output a localDateTime matching to timezone from java.time.Clock") {
      val zioEuropeAmsterdamClock: Clock.Service = Clock.Service.javaClock(europeAmsterdamFixedClock)
      val zioAmericaPhoenixClock: Clock.Service  = Clock.Service.javaClock(americaPhoenixFixedClock)
      assertM(
        ZIO.mapN(zioEuropeAmsterdamClock.localDateTime, zioAmericaPhoenixClock.localDateTime) {
          case (amsterdam, phoenix) =>
            amsterdam :: phoenix :: Nil
        }
      )(isDistinct)
    }
  )
}
