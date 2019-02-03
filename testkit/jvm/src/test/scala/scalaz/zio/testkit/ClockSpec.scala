package scalaz.zio.testkit

import scalaz.zio._
import java.util.concurrent.TimeUnit

import scalaz.zio.clock.Clock

class ClockSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "ClockSpec".title ^ s2"""
      Sleep does not sleep forever                      $e1
      Nano time is monotonically increasing             $e2
      Current time millis is monotonically increasing   $e3
     """

  val Live = Clock.Live.clock

  def e1 =
    unsafeRun(
      for {
        _ <- Live.sleep(1, TimeUnit.MILLISECONDS)
      } yield true must beTrue
    )

  def e2 =
    unsafeRun(
      for {
        time1 <- Live.nanoTime
        _     <- Live.sleep(1, TimeUnit.MILLISECONDS)
        time2 <- Live.nanoTime
      } yield (time1 < time2) must beTrue
    )

  def e3 =
    unsafeRun(
      for {
        time1 <- Live.currentTime(TimeUnit.MILLISECONDS)
        _     <- Live.sleep(1, TimeUnit.MILLISECONDS)
        time2 <- Live.currentTime(TimeUnit.MILLISECONDS)
      } yield (time1 < time2) must beTrue
    )

  def e4 =
    unsafeRun(
      for {
        ref       <- Ref.make(TestClock.Zero)
        testClock <- IO.succeed(TestClock(ref))
        result <- (for {
                   time1  <- testClock.currentTime(TimeUnit.MILLISECONDS)
                   _      <- testClock.sleep(1, TimeUnit.MILLISECONDS)
                   time2  <- testClock.currentTime(TimeUnit.MILLISECONDS)
                   sleeps <- testClock.sleeps
                 } yield
                   (sleeps must_=== List((1, TimeUnit.MILLISECONDS))) and
                     ((time2 - time1) must_=== 1)).provide(new Clock { val clock = testClock })

      } yield result
    )
}
