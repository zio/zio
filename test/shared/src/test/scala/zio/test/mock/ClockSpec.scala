package zio.test.mock

import java.util.concurrent.TimeUnit
import java.time.ZoneId

import zio._
import zio.duration._
import zio.test.mock.MockClock.DefaultData
import zio.test.TestUtils.label

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object ClockSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(e1, "sleep does sleep instantly"),
    label(e2, "sleep passes nanotime correctly"),
    label(e3, "sleep passes currentTime correctly"),
    label(e4, "sleep passes currentDateTime correctly"),
    label(e5, "sleep correctly records sleeps"),
    label(e6, "adjust correctly advances nanotime"),
    label(e7, "adjust correctly advances currentTime"),
    label(e8, "adjust correctly advances currentDateTime"),
    label(e9, "adjust does not produce sleeps "),
    label(e10, "setTime correctly sets nanotime"),
    label(e11, "setTime correctly sets currentTime"),
    label(e12, "setTime correctly sets currentDateTime"),
    label(e13, "setTime does not produce sleeps "),
    label(e14, "setTimeZone correctly sets timeZone"),
    label(e15, "setTimeZone does not produce sleeps ")
  )

  def e1 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        result    <- mockClock.sleep(10.hours).timeout(100.milliseconds)
      } yield result.nonEmpty
    )

  def e2 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.nanoTime
        _         <- mockClock.sleep(1.millis)
        time2     <- mockClock.nanoTime
      } yield (time2 - time1) == 1000000L
    )

  def e3 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- mockClock.sleep(1.millis)
        time2     <- mockClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) == 1L
    )

  def e4 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        time1     <- mockClock.currentDateTime
        _         <- mockClock.sleep(1.millis)
        time2     <- mockClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) == 1L
    )

  def e5 =
    unsafeRunToFuture(
      for {
        mockClock <- MockClock.makeMock(DefaultData)
        _         <- mockClock.sleep(1.millis)
        sleeps    <- mockClock.sleeps
      } yield sleeps == List(1.milliseconds)
    )

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
}
