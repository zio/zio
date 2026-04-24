# Instrumentation Examples

> The trait below is used in the
[zio metrics sample application](https://github.com/zio/zio-metrics-connectors/blob/docs/sample-app/src/main/scala/sample/SamplePrometheusStatsDApp.scala)
that is provided in the zio-metrics-connectors repository on GitHub. It shows how the individual metrics can be
configured and used.

The trait below is used in the
[zio metrics sample application](https://github.com/zio/zio-metrics-connectors/blob/docs/sample-app/src/main/scala/sample/SamplePrometheusStatsDApp.scala)
that is provided in the zio-metrics-connectors repository on GitHub. It shows how the individual metrics can be
configured and used.

Please refer to [StatsD](statsd-client.md) and [Prometheus](prometheus-client.md) to see how the captured metrics can be
visualized in the supported back ends.

```scala
trait InstrumentedSample {

  // Create a gauge, it can be applied to effects yielding a Double
  val aspGauge1 = Metric.gauge("gauge1", "Description of gauge1")
    .tagged("tag1", "tag value")
    .tagged("tag2", "anoter tag value")

  val aspGauge2 = Metric.gauge("gauge2")

  // Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
  // It also can be applied to effects yielding a Double
  val aspHistogram =
    Metric.histogram("my_histogram", MetricKeyType.Histogram.Boundaries.linear(0.0d, 10.0d, 11))

  // Create a summary that can hold 100 samples, the max age of the samples is 1 day.
  // The summary should report th 10%, 50% and 90% Quantile
  // It can be applied to effects yielding an Int
  val aspSummary =
    Metric.summary("my_summary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9)).contramap[Int](_.toDouble)

  // Create a Set to observe the occurrences of unique Strings
  // It can be applied to effects yielding a String
  val aspSet = Metric.frequency("my_set")

  // Create a counter applicable to any effect
  val aspCountAll = Metric.counter("count_all").contramap[Any](_ => 1L)

  private lazy val gaugeSomething = for {
    _ <- Random.nextDoubleBetween(0.0d, 100.0d) @@ aspGauge1 @@ aspCountAll
    _ <- Random.nextDoubleBetween(-50d, 50d) @@ aspGauge2 @@ aspCountAll
  } yield ()

  // Record something into a histogram
  private lazy val observeNumbers = for {
    _ <- Random.nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram @@ aspCountAll
    _ <- Random.nextIntBetween(100, 500) @@ aspSummary @@ aspCountAll
  } yield ()

  // Observe Strings in order to capture unique values
  private lazy val observeKey = for {
    _ <- Random.nextIntBetween(10, 20).map(v => s"my_key_$v") @@ aspSet @@ aspCountAll
  } yield ()

  def program: ZIO[Any, Nothing, Unit] = for {
    _ <- gaugeSomething.schedule(Schedule.spaced(200.millis)).forkDaemon
    _ <- observeNumbers.schedule(Schedule.spaced(150.millis)).forkDaemon
    _ <- observeKey.schedule(Schedule.spaced(300.millis)).forkDaemon
  } yield ()
}
```
