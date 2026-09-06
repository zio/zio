package zio

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private[zio] trait ClockSyntaxPlatformSpecific {
  final protected def toChronoUnit(unit: TimeUnit): ChronoUnit =
    unit match {
      case TimeUnit.NANOSECONDS  => ChronoUnit.NANOS
      case TimeUnit.MICROSECONDS => ChronoUnit.MICROS
      case TimeUnit.MILLISECONDS => ChronoUnit.MILLIS
      case TimeUnit.SECONDS      => ChronoUnit.SECONDS
      case TimeUnit.MINUTES      => ChronoUnit.MINUTES
      case TimeUnit.HOURS        => ChronoUnit.HOURS
      case TimeUnit.DAYS         => ChronoUnit.DAYS
    }
}
