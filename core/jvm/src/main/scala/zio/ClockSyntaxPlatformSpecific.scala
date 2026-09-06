package zio

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private[zio] trait ClockSyntaxPlatformSpecific {
  @inline final protected def toChronoUnit(unit: TimeUnit): ChronoUnit =
    unit.toChronoUnit
}
