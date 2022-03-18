package zio.metrics.jvm

import zio._

final case class JvmMetricsSchedule(value: Schedule[Any, Any, Unit])

object JvmMetricsSchedule {
  val default: ULayer[JvmMetricsSchedule] = ZLayer.succeed(JvmMetricsSchedule(Schedule.fixed(10.seconds).unit))
}
