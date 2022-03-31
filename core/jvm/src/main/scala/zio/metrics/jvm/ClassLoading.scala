package zio.metrics.jvm

import zio._
import zio.metrics.{Metric, MetricState, PollingMetric}

import java.lang.management.{ClassLoadingMXBean, ManagementFactory}

final case class ClassLoading(
  loadedClassCount: PollingMetric[Any, Throwable, MetricState.Gauge],
  totalLoadedClassCount: PollingMetric[Any, Throwable, MetricState.Gauge],
  unloadedClassCount: PollingMetric[Any, Throwable, MetricState.Gauge]
)

object ClassLoading {
  val live: ZLayer[JvmMetricsSchedule, Throwable, ClassLoading] =
    ZLayer.scoped {
      for {
        classLoadingMXBean <- ZIO.attempt(ManagementFactory.getPlatformMXBean(classOf[ClassLoadingMXBean]))
        loadedClassCount =
          PollingMetric(
            Metric
              .gauge("jvm_classes_loaded")
              .contramap[Int](_.toDouble),
            ZIO.attempt(classLoadingMXBean.getLoadedClassCount)
          )
        totalLoadedClassCount =
          PollingMetric(
            Metric
              .gauge("jvm_classes_loaded_total")
              .contramap[Long](_.toDouble),
            ZIO.attempt(classLoadingMXBean.getTotalLoadedClassCount)
          )
        unloadedClassCount =
          PollingMetric(
            Metric
              .gauge("jvm_classes_unloaded_total")
              .contramap[Long](_.toDouble),
            ZIO.attempt(classLoadingMXBean.getUnloadedClassCount)
          )

        schedule <- ZIO.service[JvmMetricsSchedule]
        _        <- loadedClassCount.launch(schedule.updateMetrics)
        _        <- totalLoadedClassCount.launch(schedule.updateMetrics)
        _        <- unloadedClassCount.launch(schedule.updateMetrics)
      } yield ClassLoading(loadedClassCount, totalLoadedClassCount, unloadedClassCount)
    }
}
