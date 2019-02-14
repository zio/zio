package scalaz.zio.testkit

import scalaz.zio._
import scalaz.zio.duration._
import java.util.concurrent.TimeUnit

import scalaz.zio.clock.Clock

class ClockSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "ClockSpec".title ^ s2"""
      Sleep does not sleep forever                      $e1
      Nano time is monotonically increasing             $e2
      Current time millis is monotonically increasing   $e3
     """

  def e1 =
    unsafeRun(
      for {
        _ <- clock.sleep(1.millis)
      } yield true must beTrue
    )

  def e2 =
    unsafeRun(
      for {
        time1 <- clock.nanoTime
        _     <- clock.sleep(1.millis)
        time2 <- clock.nanoTime
      } yield (time1 < time2) must beTrue
    )

  def e3 =
    unsafeRun(
      for {
        time1 <- clock.currentTime(TimeUnit.MILLISECONDS)
        _     <- clock.sleep(1.millis)
        time2 <- clock.currentTime(TimeUnit.MILLISECONDS)
      } yield (time1 < time2) must beTrue
    )

  def e4 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock <- IO.succeed(TestClock(ref))
        result <- (for {
                   time1  <- testClock.currentTime(TimeUnit.MILLISECONDS)
                   _      <- testClock.sleep(1.millis)
                   time2  <- testClock.currentTime(TimeUnit.MILLISECONDS)
                   sleeps <- testClock.sleeps
                 } yield
                   (sleeps must_=== List((1.millis))) and
                     ((time2 - time1) must_=== 1)).provide(new Clock with scheduler.Scheduler {
                   val clock = testClock; val scheduler = scalaz.zio.scheduler.SchedulerLive
                 })

      } yield result
    )
}
