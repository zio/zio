package zio.metrics

import zio._
import zio.test._
import TestAspect._
import java.util.concurrent.TimeUnit

object PollingMetricSpec extends ZIOBaseSpec {

  def spec = suite("PollingMetricSpec")(
    test("`launch` should be interruptible.") {

      for {
        name           <- Clock.currentTime(TimeUnit.NANOSECONDS).map(ns => s"gauge-$ns")
        (gauge, metric) = makePollingGauge(name, 1.0)
        f0             <- metric.launch(Schedule.forever.delayed(_ => 250.millis))
        _              <- f0.interrupt
        state          <- gauge.value
      } yield assertTrue(state.value == 0.0)

    } @@ flaky,
    test("`launch` should update the  internal metric using the provided Schedule.") {

      for {
        name           <- Clock.currentTime(TimeUnit.NANOSECONDS).map(ns => s"gauge-$ns")
        (gauge, metric) = makePollingGauge(name, 1.0)
        f0             <- metric.launch(Schedule.once)
        _              <- f0.join
        state          <- gauge.value
      } yield assertTrue(state.value == 1.0)

    },
    test("collectAll should generate a metric that polls all the provided metrics") {
      val gaugeIncrement1 = 1.0
      val gaugeIncrement2 = 2.0
      val pollingCount    = 2
      for {
        name1            <- Clock.currentTime(TimeUnit.NANOSECONDS).map(ns => s"gauge1-$ns")
        name2            <- Clock.currentTime(TimeUnit.NANOSECONDS).map(ns => s"gauge2-$ns")
        (gauge1, metric1) = makePollingGauge(name1, gaugeIncrement1)
        (gauge2, metric2) = makePollingGauge(name2, gaugeIncrement2)
        metric            = PollingMetric.collectAll(Seq(metric1, metric2))
        f0               <- metric.launch(Schedule.recurs(pollingCount))
        _                <- f0.join
        state1           <- gauge1.value
        state2           <- gauge2.value
      } yield assertTrue(
        state1.value == gaugeIncrement1 * pollingCount && state2.value == gaugeIncrement2 * pollingCount
      )
    }
  ) @@ withLiveClock @@ TestAspect.exceptNative

  private def makePollingGauge(name: String, increment: Double) = {
    val gauge = Metric.gauge(name)

    def metric = PollingMetric(
      gauge,
      gauge.value.map(_.value + increment)
    )

    (gauge, metric)
  }
}
