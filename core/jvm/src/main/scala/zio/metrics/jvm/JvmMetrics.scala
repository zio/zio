package zio.metrics.jvm

import zio._

trait JvmMetrics extends JvmMetricsVersionSpecific {
  protected val collectionSchedule: Schedule[Any, Any, Unit] = Schedule.fixed(10.seconds).unit

  val collectMetrics: ZManaged[Has[Clock] with Has[System], Throwable, Unit]
}
