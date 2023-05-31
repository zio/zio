package zio.metrics

import zio.metrics.MetricKeyType.Histogram
import zio.test.assertTrue
import zio.{Chunk, ZIO, ZIOBaseSpec, durationInt}

object CurrentMetricsSpec extends ZIOBaseSpec {
  private val labels = Set(MetricLabel("x", "a"), MetricLabel("y", "b"))

  private val counter   = Metric.counter("test_counter").tagged(labels).fromConst(1L)
  private val gauge     = Metric.gauge("test_gauge").tagged(labels)
  private val frequency = Metric.frequency("test_frequency").tagged(labels)
  private val histogram =
    Metric.histogram("test_histogram", Histogram.Boundaries.fromChunk(Chunk(1.0, 2.0, 3.0))).tagged(labels)
  private val summary =
    Metric.summary("test_summary", 1.minute, 10, 0.0, Chunk(0.1, 0.5, 0.9)).tagged(labels)

  def spec = suite("CurrentMetrics")(
    test("should be pretty printed correctly") {
      for {
        snapshotBefore <- CurrentMetrics.snapshot()
        str0           <- snapshotBefore.prettyPrint
        _              <- ZIO.succeed(1.0) @@ counter @@ gauge @@ histogram @@ summary
        _              <- ZIO.succeed(3.0) @@ counter @@ gauge @@ histogram @@ summary
        _              <- ZIO.succeed("strValue1") @@ frequency
        _              <- ZIO.succeed("strValue2") @@ frequency
        _              <- ZIO.succeed("strValue1") @@ frequency
        snapshotAfter  <- CurrentMetrics.snapshot()
        str1           <- snapshotAfter.prettyPrint
      } yield assertTrue(
        str0 == "" &&
          str1 == """test_counter    tags[x: a, y: b]  Counter[2.0]
                    |test_frequency  tags[x: a, y: b]  Frequency[(strValue2 -> 1), (strValue1 -> 2)]
                    |test_gauge      tags[x: a, y: b]  Gauge[3.0]
                    |test_histogram  tags[x: a, y: b]  Histogram[buckets: [(1.0 -> 1), (2.0 -> 1), (3.0 -> 2), (1.7976931348623157E308 -> 2)], count: [2], min: [1.0], max: [3.0], sum: [4.0]]
                    |test_summary    tags[x: a, y: b]  Summary[quantiles: [(0.1 -> None), (0.5 -> Some(1.0)), (0.9 -> Some(1.0))], count: [2], min: [1.0], max: [3.0], sum: [4.0]]""".stripMargin
      )
    }
  )

}
