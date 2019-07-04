package zio.testkit

import java.util.concurrent.TimeUnit

import zio._
import zio.duration._

//remove this comment to make a commit
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
     """

  def e1 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        result    <- testClock.sleep(10.hours).timeout(100.milliseconds)
      } yield result.nonEmpty must beTrue
    )

  def e2 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        time1     <- testClock.nanoTime
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.nanoTime
      } yield (time2 - time1) must_== 1000000L
    )

  def e3 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        time1     <- testClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) must_== 1L
    )

  def e4 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        time1     <- testClock.currentDateTime
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) must_== 1L
    )

  def e5 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        _         <- testClock.sleep(1.millis)
        sleeps    <- testClock.sleeps
      } yield sleeps must_== List(1.milliseconds)
    )

  def e6 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        time1     <- testClock.nanoTime
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.nanoTime
      } yield (time2 - time1) must_== 1000000L
    )

  def e7 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        time1     <- testClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) must_== 1L
    )

  def e8 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        time1     <- testClock.currentDateTime
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.currentDateTime
      } yield (time2.toInstant.toEpochMilli - time1.toInstant.toEpochMilli) must_== 1L
    )

  def e9 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock = TestClock(ref)
        _         <- testClock.adjust(1.millis)
        sleeps    <- testClock.sleeps
      } yield sleeps must_== Nil
    )

}
