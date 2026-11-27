package zio.metrics.jvm

import zio._
import zio.metrics.{Metric, MetricState, PollingMetric}

import java.lang.management.{ClassLoadingMXBean, ManagementFactory}

/**
 * The polling metrics of JVM class loading (`ClassLoadingMXBean`). Each one is
 * a gauge.
 *
 * @param loadedClassCount
 *   `jvm_classes_loaded`: the number of classes currently loaded
 * @param totalLoadedClassCount
 *   `jvm_classes_loaded_total`: the number of classes loaded since the JVM
 *   started
 * @param unloadedClassCount
 *   `jvm_classes_unloaded_total`: the number of classes unloaded since the JVM
 *   started
 */
final case class ClassLoading(
  loadedClassCount: PollingMetric[Any, Throwable, MetricState.Gauge],
  totalLoadedClassCount: PollingMetric[Any, Throwable, MetricState.Gauge],
  unloadedClassCount: PollingMetric[Any, Throwable, MetricState.Gauge]
)

/**
 * The layer that collects the class loading metrics.
 */
object ClassLoading {

  /**
   * A layer that polls the class loading metrics on the `updateMetrics`
   * schedule of [[JvmMetricsSchedule]], until the layer's scope closes.
   */
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
