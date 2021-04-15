/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.time.{Duration => JavaDuration, Instant, OffsetDateTime}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration}

object Duration {
  val Infinity: Duration = java.time.Duration.ofNanos(Long.MaxValue)
  val Zero               = java.time.Duration.ZERO

  object Finite {

    def apply(nanos: Long): Duration =
      if (nanos >= 0) nanos.nanos
      else Duration.Zero

    def unapply(arg: Duration): Option[Long] = if (arg >= Duration.Infinity) None else Some(arg.toNanos)

  }

  def apply(amount: Long, unit: TimeUnit): Duration = fromNanos(unit.toNanos(amount))

  def fromMillis(millis: Long): Duration = java.time.Duration.ofMillis(millis)

  def fromNanos(nanos: Long): Duration = nanos.nanos

  def fromInterval(start: Instant, end: Instant): Duration =
    java.time.Duration.between(start, end)

  def fromInterval(start: OffsetDateTime, end: OffsetDateTime): Duration =
    java.time.Duration.between(start, end)

  def fromInstant(instant: Instant): Duration =
    Duration(instant.toEpochMilli, TimeUnit.MILLISECONDS)

  def fromScala(duration: ScalaDuration): Duration = duration match {
    case d: ScalaFiniteDuration => fromNanos(d.toNanos)
    case _                      => Infinity
  }

  def fromJava(duration: JavaDuration): Duration =
    if (duration.isNegative) Zero
    else if (duration.compareTo(JavaDuration.ofNanos(Long.MaxValue)) >= 0) Infinity
    else fromNanos(duration.toNanos)
}

class DurationSyntax(n: Long) {
  private[this] def asDuration(unit: TemporalUnit): Duration =
    if (n >= 0) java.time.Duration.of(n, unit) else Duration.Zero

  def nanoseconds: Duration = asDuration(ChronoUnit.NANOS)
  def nanos                 = nanoseconds
  def nanosecond            = nanoseconds
  def nano                  = nanoseconds

  def microseconds: Duration = asDuration(ChronoUnit.MICROS)
  def micros                 = microseconds
  def microsecond            = microseconds
  def micro                  = microseconds

  def milliseconds: Duration = asDuration(ChronoUnit.MILLIS)
  def millis                 = milliseconds
  def millisecond            = milliseconds
  def milli                  = milliseconds

  def seconds: Duration = asDuration(ChronoUnit.SECONDS)
  def second            = seconds

  def minutes: Duration = asDuration(ChronoUnit.MINUTES)
  def minute            = minutes

  def hours: Duration = asDuration(ChronoUnit.HOURS)
  def hour            = hours

  def days: Duration = asDuration(ChronoUnit.DAYS)
  def day            = days

}
