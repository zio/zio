package zio.metrics

import zio._
import zio.test._
import TestAspect._
import java.util.concurrent.TimeUnit

object PollingMetricSpec extends ZIOSpecDefault {

  def spec = suite("PollingMetricSpec")(
    test("`launch` should be interruptible.") {

      for {
        name           <- Clock.currentTime(TimeUnit.NANOSECONDS).map(ns => s"gauge-$ns")
        (gauge, metric) = makePollingGauge(name)
        f0             <- metric.launch(Schedule.forever.delayed(_ => 250.millis))
        _              <- f0.interrupt
        state          <- gauge.value
      } yield assertTrue(state.value == 0.0)

    } @@ flaky,
    test("`launch` should update the  internal metric using the provided Schedule.") {

      for {
        name           <- Clock.currentTime(TimeUnit.NANOSECONDS).map(ns => s"gauge-$ns")
        (gauge, metric) = makePollingGauge(name)
        f0             <- metric.launch(Schedule.once)
        _              <- f0.join
        state          <- gauge.value
      } yield assertTrue(state.value == 1.0)

    }
  ) @@ withLiveClock

  private def makePollingGauge(name: String) = {
    val gauge = Metric.gauge(name)

    def metric = PollingMetric(
      gauge,
      gauge.value.map(_.value + 1.0)
    )

    (gauge, metric)

  }

}
