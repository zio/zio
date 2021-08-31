package zio.internal.metrics

/**
 * A `MetricListener` is capable of taking some action in response to a metric
 * being recorded, such as sending that metric to a third party service.
 */
trait MetricListener {
  def gaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit

  def counterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): Unit

  def histogramChanged(key: MetricKey.Histogram, value: MetricState): Unit

  def summaryChanged(key: MetricKey.Summary, value: MetricState): Unit

  def setChanged(key: MetricKey.SetCount, value: MetricState): Unit
}

object MetricListener {
  val none: MetricListener =
    new MetricListener {
      def gaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit =
        ()
      def counterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): Unit =
        ()
      def histogramChanged(key: MetricKey.Histogram, value: MetricState): Unit =
        ()
      def summaryChanged(key: MetricKey.Summary, value: MetricState): Unit =
        ()
      def setChanged(key: MetricKey.SetCount, value: MetricState): Unit =
        ()
    }
}
