package zio.test.mock

import java.util.concurrent.TimeUnit
import java.time.ZoneId

import zio._
import zio.duration._
import zio.test.mock.TestClock.DefaultData

class ClockSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "ClockSpec".title ^ s2"""
      Sleep does sleep instantly                        $e1
      Sleep passes nanotime correctly                   $e2
      Sleep passes currentTime correctly                $e3
      Sleep passes currentDateTime correctly            $e4
      Sleep correctly records sleeps                    $e5
      Adjust correctly advances nanotime                $e6
      Adjust correctly advances currentTime             $e7
      Adjust correctly advances currentDateTime         $e8
      Adjust does not produce sleeps                    $e9
      SetTime correctly sets nanotime                   $e10
      SetTime correctly sets currentTime                $e11
      SetTime correctly sets currentDateTime            $e12
      SetTime does not produce sleeps                   $e13
      SetTimeZone correctly sets timeZone               $e14
      SetTimeZone does not produce sleeps               $e15
     """

  def e1 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        result    <- testClock.sleep(10.hours).timeout(100.milliseconds)
      } yield result.nonEmpty must beTrue
    )

  def e2 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        time1     <- testClock.nanoTime
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.nanoTime
      } yield (time2 - time1) must_== 1000000L
    )

  def e3 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        time1     <- testClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) must_== 1L
    )

  def e4 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        time1     <- testClock.currentDateTime
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) must_== 1L
    )

  def e5 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.sleep(1.millis)
        sleeps    <- testClock.sleeps
      } yield sleeps must_== List(1.milliseconds)
    )

  def e6 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        time1     <- testClock.nanoTime
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.nanoTime
      } yield (time2 - time1) must_== 1000000L
    )

  def e7 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        time1     <- testClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) must_== 1L
    )

  def e8 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        time1     <- testClock.currentDateTime
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) must_== 1L
    )

  def e9 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.adjust(1.millis)
        sleeps    <- testClock.sleeps
      } yield sleeps must_== Nil
    )

  def e10 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.setTime(1.millis)
        time      <- testClock.nanoTime
      } yield time must_== 1000000L
    )

  def e11 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.setTime(1.millis)
        time      <- testClock.currentTime(TimeUnit.MILLISECONDS)
      } yield time must_== 1L
    )

  def e12 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.setTime(1.millis)
        time      <- testClock.currentDateTime
      } yield time.toInstant.toEpochMilli must_== 1L
    )

  def e13 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.setTime(1.millis)
        sleeps    <- testClock.sleeps
      } yield sleeps must_== Nil
    )

  def e14 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.setTimeZone(ZoneId.of("America/New_York"))
        timeZone  <- testClock.timeZone
      } yield timeZone must_== ZoneId.of("America/New_York")
    )

  def e15 =
    unsafeRun(
      for {
        testClock <- TestClock.make(DefaultData)
        _         <- testClock.setTimeZone(ZoneId.of("America/New_York"))
        sleeps    <- testClock.sleeps
      } yield sleeps must_== Nil
    )
}
