package zio.internal.metrics

import zio._

import zio.internal.ZMetrics._

/**
 * A `Counter` is a metric representing a single numerical value that may be
 * incremented over time. A typical use of this metric would be to track the
 * number of a certain type of request received. With a counter the quantity
 * of interest is the cumulative value over time, as opposed to a gauge where
 * the quantity of interest is the value as of a specific point in time.
 */
trait Counter {

  /**
   * Increments the counter by the specified amount.
   */
  def increment(value: Double): UIO[Any]
}

object Counter {

  /**
   * Construct a counter with the specified metric key.
   */
  def apply(key: MetricKey.Counter): Counter =
    metricState.getCounter(key)

  /**
   * Constructs a counter with the specified name and labels.
   */
  def apply(name: String, tags: Label*): Counter =
    apply(MetricKey.Counter(name, Chunk.fromIterable(tags)))

  /**
   * A counter that does nothing.
   */
  val none: Counter =
    new Counter {
      def increment(value: Double): UIO[Any] =
        ZIO.unit
    }
}
