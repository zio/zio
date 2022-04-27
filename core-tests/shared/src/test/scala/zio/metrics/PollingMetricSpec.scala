package zio.metrics

import zio._
import zio.test._
import TestAspect._

object PollingMetricSpec extends ZIOSpecDefault {

  def spec = suite("PollingMetricSpec")(
    test("`launch` should be interruptible.") {

      val metric = PollingMetric(
        Metric
          .gauge("gauge"),
        ZIO.succeed(1.0)
      )

      val pgm = for {
        ref <- Ref.make(0L)
        f0 <-
          metric.launch(
            Schedule.recurs(3).tapOutput(l => ref.update(_ + l)).delayed(_ => 250.millis)
          )
        _     <- f0.interrupt
        count <- ref.get
      } yield assertTrue(count < 6L)

      pgm
    }
  ) @@ withLiveClock

}
