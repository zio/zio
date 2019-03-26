package scalaz.zio.testkit

import java.util.concurrent.TimeUnit

import scalaz.zio._
import scalaz.zio.duration._

class ClockSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "ClockSpec".title ^ s2"""
      Sleep does sleep instantly                        $e1
      Sleep passes nanotime correctly                   $e2
      Sleep passes currentTime correctly                $e3
      Sleep correctly records sleeps                    $e4
      Adjust correctly advances nanotime                $e5
      Adjust correctly advances currentTime             $e6
      Adjust does not produce sleeps                    $e7
     """

  def e1 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock  = TestClock(ref)
        result    <- testClock.sleep(10.hours).timeout(100.milliseconds)
      } yield result.nonEmpty must beTrue
    )

  def e2 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock  = TestClock(ref)
        time1     <- testClock.nanoTime
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.nanoTime
      } yield (time2 - time1) must_== 1000000L
    )

  def e3 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock  = TestClock(ref)
        time1     <- testClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- testClock.sleep(1.millis)
        time2     <- testClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) must_== 1L
    )

  def e4 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock  = TestClock(ref)
        _         <- testClock.sleep(1.millis)
        sleeps    <- testClock.sleeps
      } yield sleeps must_== List(1.milliseconds)
    )

  def e5 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock  = TestClock(ref)
        time1     <- testClock.nanoTime
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.nanoTime
      } yield (time2 - time1) must_== 1000000L
    )

  def e6 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock  = TestClock(ref)
        time1     <- testClock.currentTime(TimeUnit.MILLISECONDS)
        _         <- testClock.adjust(1.millis)
        time2     <- testClock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time2 - time1) must_== 1L
    )

  def e7 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock  = TestClock(ref)
        _         <- testClock.adjust(1.millis)
        sleeps    <- testClock.sleeps
      } yield sleeps must_== Nil
    )


}
