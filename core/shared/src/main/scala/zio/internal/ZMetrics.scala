package zio.internal

import zio._
import zio.internal.metrics._

import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray, DoubleAdder, LongAdder}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque}

trait ZMetrics {
  def snapshot: Map[MetricKey, MetricState]
  def metricListener: MetricListener
}

object ZMetrics {

  type Label = (String, String)

  val default: ZMetrics =
    new ZMetrics {

      def snapshot: Map[MetricKey, MetricState] = {
        val iterator = metricState.map.entrySet().iterator()
        val result   = scala.collection.mutable.Map[MetricKey, MetricState]()
        while (iterator.hasNext) {
          val value = iterator.next()
          result.put(value.getKey(), value.getValue().toMetricState)
        }
        result.toMap
      }
      val metricListener: MetricListener =
        MetricListener.none
    }

  private lazy val metricState: ConcurrentState =
    new ConcurrentState
}
