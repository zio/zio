package zio.internal.metrics

import zio._

sealed trait MetricType

object MetricType {

  final case class Counter(count: Double) extends MetricType

  final case class Gauge(value: Double) extends MetricType

  final case class DoubleHistogram(
    buckets: Chunk[(Double, Long)],
    count: Long,
    sum: Double
  ) extends MetricType

  final case class Summary(
    error: Double,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    sum: Double
  ) extends MetricType

  final case class SetCount(
    setTag: String,
    occurrences: Chunk[(String, Long)]
  ) extends MetricType

}
