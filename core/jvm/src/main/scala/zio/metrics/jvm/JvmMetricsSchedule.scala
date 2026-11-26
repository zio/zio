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

/**
 * The default schedules for the JVM metrics.
 */
object JvmMetricsSchedule {

  /**
   * Updates each JVM metric with `Schedule.fixed(10.seconds)` and reloads the
   * dynamic JVM metrics with `Schedule.fixed(1.minute)`. The first update of a
   * polled metric happens 10 seconds after its layer starts.
   */
  val default: ULayer[JvmMetricsSchedule] =
    ZLayer.succeed(JvmMetricsSchedule(Schedule.fixed(10.seconds), Schedule.fixed(1.minute)))
}
