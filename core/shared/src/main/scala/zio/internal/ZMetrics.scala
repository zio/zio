package zio.internal

import zio.internal.metrics._

trait ZMetrics {
  def snapshot: Map[MetricKey, MetricState]
  def metricListener: MetricListener
}

object ZMetrics {

  type Label = (String, String)

  val default: ZMetrics =
    new ZMetrics {

      def snapshot: Map[MetricKey, MetricState] =
        metricState.snapshot
      val metricListener: MetricListener =
        MetricListener.none
    }

  private lazy val metricState: ConcurrentState =
    new ConcurrentState
}
