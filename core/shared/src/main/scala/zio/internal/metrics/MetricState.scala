package zio.internal.metrics

import zio._
import zio.internal.ZMetrics._

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

  def counter(key: MetricKey.Counter, help: String, value: Double): MetricState =
    MetricState(key.name, help, key.tags, MetricType.Counter(value))

  def gauge(
    key: MetricKey.Gauge,
    help: String,
    startAt: Double
  ): MetricState =
    MetricState(key.name, help, key.tags, MetricType.Gauge(startAt))

  def doubleHistogram(
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

  def setCount(
    key: MetricKey.SetCount,
    help: String,
    values: Chunk[(String, Long)]
  ): MetricState = MetricState(key.name, help, key.tags, MetricType.SetCount(key.setTag, values))
}
