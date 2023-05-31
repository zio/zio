package zio.metrics

import zio.metrics.MetricKeyType.Histogram
import zio.test.assertTrue
import zio.{Chunk, ZIO, ZIOBaseSpec, durationInt}

object CurrentMetricsSpec extends ZIOBaseSpec {
  private val labels = Set(MetricLabel("x", "a"), MetricLabel("y", "b"))

  private val counter   = Metric.counter("c1").tagged(labels).fromConst(1L)
  private val gauge     = Metric.gauge("g1").tagged(labels)
  private val frequency = Metric.frequency("sc1").tagged(labels)
  private val histogram =
    Metric.histogram("h1", Histogram.Boundaries.fromChunk(Chunk(1.0, 2.0, 3.0))).tagged(labels)
  private val summary =
    Metric.summary("s1", 1.minute, 10, 0.0, Chunk(0.1, 0.5, 0.9)).tagged(labels)

  def spec = suite("Metric")(
    test("CurrentMetrics prettyPrint should look correct") {
      for {
        _              <- ZIO.succeed(1.0) @@ counter @@ gauge @@ histogram @@ summary
        _              <- ZIO.succeed(3.0) @@ counter @@ gauge @@ histogram @@ summary
        _              <- ZIO.succeed("strValue1") @@ frequency
        _              <- ZIO.succeed("strValue2") @@ frequency
        _              <- ZIO.succeed("strValue1") @@ frequency
        currentMetrics <- CurrentMetrics.snapshot()
        str            <- currentMetrics.prettyPrint
      } yield assertTrue(
        str == """c1   tags[x: a, y: b]  Counter[2.0]
                 |g1   tags[x: a, y: b]  Gauge[3.0]
                 |h1   tags[x: a, y: b]  Histogram[buckets: [(1.0 -> 1), (2.0 -> 1), (3.0 -> 2), (1.7976931348623157E308 -> 2)], count: [2], min: [1.0], max: [3.0], sum: [4.0]]
                 |s1   tags[x: a, y: b]  Summary[quantiles: [(0.1 -> None), (0.5 -> Some(1.0)), (0.9 -> Some(1.0))], count: [2], min: [1.0], max: [3.0], sum: [4.0]]
                 |sc1  tags[x: a, y: b]  Frequency[(strValue2 -> 1), (strValue1 -> 2)]""".stripMargin
      )
    },
    test("CurrentMetrics prettyPrint should correctly process empty Metric set") {
      for {
        currentMetrics <- CurrentMetrics.snapshot()
        str            <- currentMetrics.prettyPrint
      } yield assertTrue(str == "")
    }
  )

}
