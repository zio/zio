package zio.test.mock

import java.util.concurrent.TimeUnit
import java.time.ZoneId

import scala.Predef.{ assert => SAssert }

import zio._
import zio.duration._
import zio.test.mock.MockClock.DefaultData

object ClockSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(e1, "MockClock sleep does sleep instantly")
    SAssert(e2, "MockClock sleep passes nanotime correctly")
    SAssert(e3, "MockClock sleep passes currentTime correctly")
    SAssert(e4, "MockClock sleep passes currentDateTime correctly")
    SAssert(e5, "MockClock sleep correctly records sleeps")
    SAssert(e6, "MockClock adjust correctly advances nanotime")
    SAssert(e7, "MockClock adjust correctly advances currentTime")
    SAssert(e8, "MockClock adjust correctly advances currentDateTime")
    SAssert(e9, "MockClock adjust does not produce sleeps ")
    SAssert(e10, "MockClock setTime correctly sets nanotime")
    SAssert(e11, "MockClock setTime correctly sets currentTime")
    SAssert(e12, "MockClock setTime correctly sets currentDateTime")
    SAssert(e13, "MockClock setTime does not produce sleeps ")
    SAssert(e14, "MockClock setTimeZone correctly sets timeZone")
    SAssert(e15, "MockClock setTimeZone does not produce sleeps ")
  }

  def e1 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        result    <- mockClock.sleep(10.hours).timeout(100.milliseconds)
      } yield result.nonEmpty
    )

  def e2 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.nanoTime
        _         <- mockClock.sleep(1.millis)
        time2     <- mockClock.nanoTime
      } yield (time2 - time1) == 1000000L
    )

  def e3 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- mockClock.sleep(1.millis)
        time2     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) == 1L
    )

  def e4 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentDateTime
        _         <- mockClock.sleep(1.millis)
        time2     <- mockClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) == 1L
    )

  def e5 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.sleep(1.millis)
        sleeps    <- mockClock.sleeps
      } yield sleeps == List(1.milliseconds)
    )

  def e6 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.nanoTime
        _         <- mockClock.adjust(1.millis)
        time2     <- mockClock.nanoTime
      } yield (time2 - time1) == 1000000L
    )

  def e7 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- mockClock.adjust(1.millis)
        time2     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) == 1L
    )

  def e8 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentDateTime
        _         <- mockClock.adjust(1.millis)
        time2     <- mockClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) == 1L
    )

  def e9 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.adjust(1.millis)
        sleeps    <- mockClock.sleeps
      } yield sleeps == Nil
    )

  def e10 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        time      <- mockClock.nanoTime
      } yield time == 1000000L
    )

  def e11 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        time      <- mockClock.currentTime(TimeUnit.MILLISECONDS)
      } yield time == 1L
    )

  def e12 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        time      <- mockClock.currentDateTime
      } yield time.toInstant.toEpochMilli == 1L
    )

  def e13 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTime(1.millis)
        sleeps    <- mockClock.sleeps
      } yield sleeps == Nil
    )

  def e14 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTimeZone(ZoneId.of("America/New_York"))
        timeZone  <- mockClock.timeZone
      } yield timeZone == ZoneId.of("America/New_York")
    )

  def e15 =
    unsafeRun(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.setTimeZone(ZoneId.of("America/New_York"))
        sleeps    <- mockClock.sleeps
      } yield sleeps == Nil
    )
}
