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
}
