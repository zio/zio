package zio.metrics.jvm

import zio._

/**
 * Configuration for the JVM metrics
 *
 * @param updateMetrics
 *   Schedule for periodically updating each JVM metric
 * @param reloadDynamicMetrics
 *   Schedule for regenerating the dynamic JVM metrics such as buffer pool
 *   metrics
 */
final case class JvmMetricsSchedule(
  updateMetrics: Schedule[Any, Any, Any],
  reloadDynamicMetrics: Schedule[Any, Any, Any]
)

object JvmMetricsSchedule {
  val default: ULayer[JvmMetricsSchedule] =
    ZLayer.succeed(JvmMetricsSchedule(Schedule.fixed(10.seconds), Schedule.fixed(1.minute)))
}
