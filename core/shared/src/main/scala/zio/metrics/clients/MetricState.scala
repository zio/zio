package zio.metrics.clients

import zio._
import zio.metrics._

/**
 * `MetricState` represents a snapshot of the current state of a metric as of
 * a poiint in time.
 */
final case class MetricState(
  name: String,
  help: String,
  labels: Chunk[Label],
  details: MetricType
) {
  override def toString(): String = {
    val lbls = if (labels.isEmpty) "" else labels.map(l => s"${l._1}->${l._2}").mkString("{", ",", "}")
    s"MetricState($name$lbls, $details)"
  }
}

object MetricState {

  /**
   * Constructs a snapshot of the state of a counter.
   */
  def counter(key: MetricKey.Counter, help: String, value: Double): MetricState =
    MetricState(key.name, help, key.tags, MetricType.Counter(value))

  /**
   * Constructs a snapshot of the state of a gauge.
   */
  def gauge(
    key: MetricKey.Gauge,
    help: String,
    startAt: Double
  ): MetricState =
    MetricState(key.name, help, key.tags, MetricType.Gauge(startAt))

  /**
   * Constructs a snapshot of the state of a histogram.
   */
  def histogram(
    key: MetricKey.Histogram,
    help: String,
    buckets: Chunk[(Double, Long)],
    count: Long,
    sum: Double
  ): MetricState =
    MetricState(
      key.name,
      help,
      key.tags,
      MetricType.DoubleHistogram(buckets, count, sum)
    )

  /**
   * Constructs a snapshot of the state of a summary.
   */
  def summary(
    key: MetricKey.Summary,
    help: String,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    sum: Double
  ): MetricState =
    MetricState(
      key.name,
      help,
      key.tags,
      MetricType.Summary(key.error, quantiles, count, sum)
    )

  /**
   * Constructs a snapshot of the state of a set count..
   */
  def setCount(
    key: MetricKey.SetCount,
    help: String,
    values: Chunk[(String, Long)]
  ): MetricState =
    MetricState(key.name, help, key.tags, MetricType.SetCount(key.setTag, values))
}
