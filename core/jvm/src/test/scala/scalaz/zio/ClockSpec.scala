package scalaz.zio

import java.util.concurrent.TimeUnit

class ClockSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "ClockSpec".title ^ s2"""
      Sleep does not sleep forever                      $e1
      Nano time is monotonically increasing             $e2
      Current time millis is monotonically increasing   $e3
     """

  val Live = Clock.Live

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
        ref    <- Ref(Clock.Test.Zero)
        clock  <- IO.succeed(Clock.Test(ref))
        time1  <- clock.currentTime(TimeUnit.MILLISECONDS)
        _      <- clock.sleep(1, TimeUnit.MILLISECONDS)
        time2  <- clock.currentTime(TimeUnit.MILLISECONDS)
        sleeps <- clock.ref.get.map(_.sleeps)
      } yield
        (sleeps must_=== List((1, TimeUnit.MILLISECONDS))) and
          ((time2 - time1) must_=== 1)
    )
}
