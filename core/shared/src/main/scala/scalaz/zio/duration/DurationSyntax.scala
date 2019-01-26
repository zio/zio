package scalaz.zio.duration

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._

class DurationSyntax(n: Long) {

  protected def asDuration(unit: TimeUnit): Duration = Duration(n, unit)

  final def nanoseconds = asDuration(NANOSECONDS)
  final def nanos       = nanoseconds
  final def nanosecond  = nanoseconds
  final def nano        = nanoseconds

  final def microseconds = asDuration(MICROSECONDS)
  final def micros       = microseconds
  final def microsecond  = microseconds
  final def micro        = microseconds

  final def milliseconds = asDuration(MILLISECONDS)
  final def millis       = milliseconds
  final def millisecond  = milliseconds
  final def milli        = milliseconds

  final def seconds = asDuration(SECONDS)
  final def second  = seconds

  final def minutes = asDuration(MINUTES)
  final def minute  = minutes

  final def hours = asDuration(HOURS)
  final def hour  = hours

  final def days = asDuration(DAYS)
  final def day  = days

}
