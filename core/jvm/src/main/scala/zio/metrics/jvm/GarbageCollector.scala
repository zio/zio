package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio._
import zio.metrics._
import zio.metrics.Metric.Gauge
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}

import scala.collection.JavaConverters._

final case class GarbageCollector(
  gcCollectionSecondsSum: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  gcCollectionSecondsCount: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]]
)

object GarbageCollector {
  @silent("JavaConverters")
  val live: ZLayer[Clock with JvmMetricsSchedule, Throwable, GarbageCollector] =
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
        _        <- gcCollectionSecondsSum.launch(schedule.value)
        _        <- gcCollectionSecondsCount.launch(schedule.value)
      } yield GarbageCollector(gcCollectionSecondsSum, gcCollectionSecondsCount)
    }
}
