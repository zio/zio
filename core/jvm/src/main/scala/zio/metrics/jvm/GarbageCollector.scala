package zio.metrics.jvm

import zio._
import zio.metrics._

import java.lang.management.ManagementFactory
import scala.jdk.CollectionConverters._

/**
 * The polling metrics of the JVM garbage collectors (`GarbageCollectorMXBean`).
 * Each one is a gauge per collector, tagged with `gc` and the name of the
 * collector.
 *
 * @param gcCollectionSecondsSum
 *   `jvm_gc_collection_seconds_sum`: the time that each collector has spent
 *   collecting, in seconds
 * @param gcCollectionSecondsCount
 *   `jvm_gc_collection_seconds_count`: the number of collections that each
 *   collector has run
 */
final case class GarbageCollector(
  gcCollectionSecondsSum: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  gcCollectionSecondsCount: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]]
)

/**
 * The layer that collects the garbage collector metrics.
 */
object GarbageCollector {

  /**
   * A layer that polls the garbage collector metrics on the `updateMetrics`
   * schedule of [[JvmMetricsSchedule]], until the layer's scope closes.
   */
  val live: ZLayer[JvmMetricsSchedule, Throwable, GarbageCollector] =
    ZLayer.scoped {
      for {
        gcMXBeans <- ZIO.attempt(ManagementFactory.getGarbageCollectorMXBeans.asScala)
        gcCollectionSecondsSum = PollingMetric.collectAll(gcMXBeans.map { gc =>
                                   PollingMetric(
                                     Metric
                                       .gauge("jvm_gc_collection_seconds_sum")
                                       .tagged("gc", gc.getName)
                                       .contramap((ms: Long) => ms.toDouble / 1000.0),
                                     ZIO.attempt(gc.getCollectionTime)
                                   )
                                 })
        gcCollectionSecondsCount = PollingMetric.collectAll(gcMXBeans.map { gc =>
                                     PollingMetric(
                                       Metric
                                         .gauge("jvm_gc_collection_seconds_count")
                                         .tagged("gc", gc.getName)
                                         .contramap[Long](_.toDouble),
                                       ZIO.attempt(gc.getCollectionCount)
                                     )
                                   })

        schedule <- ZIO.service[JvmMetricsSchedule]
        _        <- gcCollectionSecondsSum.launch(schedule.updateMetrics)
        _        <- gcCollectionSecondsCount.launch(schedule.updateMetrics)
      } yield GarbageCollector(gcCollectionSecondsSum, gcCollectionSecondsCount)
    }
}
