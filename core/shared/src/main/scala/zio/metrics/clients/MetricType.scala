package zio.metrics.clients

import zio._

/**
 * `MetricType` represents information about the state of a metric that is
 * particular to a certain type of metric, such as a histogram as opposed to a
 * counter.
 */
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
