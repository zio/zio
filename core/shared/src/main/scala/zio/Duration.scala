/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.time.{Duration => JavaDuration, Instant, OffsetDateTime}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration}
import scala.language.implicitConversions

/**
 * Mixed into the `zio` package object: defines the `Duration` type alias, the
 * implicit conversions that give numbers the duration syntax of
 * [[DurationSyntax]] (`5.seconds`) and durations the operators of
 * [[DurationOps]], and an `Ordering` for durations.
 */
trait DurationModule {

  /**
   * ZIO's duration type: an alias for `java.time.Duration`, so every Java
   * duration is a ZIO duration. Build one with the syntax on numbers
   * (`5.seconds`, `250.millis`) or with the constructors in the `Duration`
   * object. ZIO adds saturating arithmetic, comparison and `render` through an
   * implicit conversion to [[DurationOps]].
   *
   * A `java.time.Duration` can be negative and can be longer than
   * `Duration.Infinity` (`Long.MaxValue` nanoseconds, about 292 years). The ZIO
   * operations that read the length in nanoseconds (`+`, `*`, `render`,
   * `compare`, `asScala`) throw `java.lang.ArithmeticException` when the length
   * does not fit in a `Long` of nanoseconds: longer than `Duration.Infinity`,
   * or more than about 292 years negative.
   */
  type Duration = JavaDuration

  /**
   * Converts an `Int` to [[DurationSyntax]], so that `5.seconds` builds a
   * `Duration`.
   */
  implicit def durationInt(n: Int): DurationSyntax = new DurationSyntax(n.toLong)

  /**
   * Converts a `Long` to [[DurationSyntax]], so that `5L.seconds` builds a
   * `Duration`.
   */
  implicit def durationLong(n: Long): DurationSyntax = new DurationSyntax(n)

  /**
   * Adds the operators of [[DurationOps]] (`+`, `*`, `render`, `fold`, ...) to
   * every `Duration`.
   */
  implicit def duration2DurationOps(duration: Duration): DurationOps = new DurationOps(duration)

  /**
   * Orders durations by length with `java.time.Duration.compareTo`. Negative
   * durations sort before `Duration.Zero`.
   */
  implicit val durationOrdering: Ordering[Duration] = new Ordering[Duration] {
    override def compare(x: Duration, y: Duration): Int = x.compareTo(y)
  }

}

/**
 * Constructors and constants for `Duration`, ZIO's alias for
 * `java.time.Duration`.
 *
 * Most constructors keep negative lengths (`fromNanos(-1)` is a negative
 * duration); `Finite.apply` and `fromJava` clamp them to `Duration.Zero`.
 *
 * {{{
 * val timeout: Duration = 5.seconds
 * val total: Duration   = timeout + 500.millis
 * val label: String     = total.render
 * // "5 s 500 ms"
 * }}}
 */
object Duration {

  /**
   * The duration that ZIO treats as infinite: `Long.MaxValue` nanoseconds
   * (106751 days 23 hours 47 minutes 16.854775807 seconds). Sleeping for it
   * with the live clock does not resume, `+` and `*` saturate at it, and `fold`
   * picks its `infinity` branch for it.
   */
  val Infinity: Duration = JavaDuration.ofNanos(Long.MaxValue)

  /** A duration of length zero (`java.time.Duration.ZERO`). */
  val Zero: Duration = JavaDuration.ZERO

  /**
   * Builds and matches durations shorter than `Duration.Infinity`.
   */
  object Finite {

    /**
     * Returns a duration of `nanos` nanoseconds, or `Duration.Zero` when
     * `nanos` is negative.
     */
    def apply(nanos: Long): Duration =
      if (nanos >= 0) nanos.nanos
      else Duration.Zero

    /**
     * Returns the length in nanoseconds of a duration shorter than
     * `Duration.Infinity`, negative durations down to about -292 years
     * included. Returns `None` for `Duration.Infinity` and for longer
     * durations, and throws `java.lang.ArithmeticException` for a more negative
     * duration.
     */
    def unapply(arg: Duration): Option[Long] = if (arg >= Duration.Infinity) None else Some(arg.toNanos)

  }

  /**
   * Returns a duration of `amount` units, for example `Duration(3,
   * TimeUnit.MINUTES)`. A negative amount gives a negative duration. Throws
   * `java.lang.ArithmeticException` when the result does not fit in a
   * `java.time.Duration` (for example `Long.MaxValue` days).
   */
  def apply(amount: Long, unit: TimeUnit): Duration = apply(amount, toChronoUnit(unit))

  /**
   * Returns a duration of `amount` units, for example `Duration(3,
   * ChronoUnit.MINUTES)`. A negative amount gives a negative duration, and
   * `ChronoUnit.DAYS` counts as exactly 24 hours. Units longer than a day
   * (`WEEKS`, `MONTHS`, `YEARS`, ...) throw
   * `java.time.temporal.UnsupportedTemporalTypeException` ("Unit must not have
   * an estimated duration"), and a result that does not fit in a
   * `java.time.Duration` throws `java.lang.ArithmeticException`.
   */
  def apply(amount: Long, unit: ChronoUnit): Duration = JavaDuration.of(amount, unit)

  /**
   * Returns a duration of `millis` milliseconds. A negative value gives a
   * negative duration, and `Long.MaxValue` gives a duration longer than
   * `Duration.Infinity`.
   */
  def fromMillis(millis: Long): Duration = JavaDuration.ofMillis(millis)

  /**
   * Returns a duration of `nanos` nanoseconds. A negative value gives a
   * negative duration (`Finite(nanos)` clamps it to zero instead), and
   * `fromNanos(Long.MaxValue)` equals `Duration.Infinity`.
   */
  def fromNanos(nanos: Long): Duration = nanos.nanos

  /**
   * Returns the time from `start` to `end`, which is negative when `end` is
   * before `start`.
   */
  def fromInterval(start: Instant, end: Instant): Duration =
    JavaDuration.between(start, end)

  /**
   * Returns the time from `start` to `end`, taking their offsets into account
   * (10:00+01:00 to 10:00Z is one hour). It is negative when `end` is before
   * `start`.
   */
  def fromInterval(start: OffsetDateTime, end: OffsetDateTime): Duration =
    JavaDuration.between(start, end)

  /**
   * Returns the time from the Unix epoch to `instant` in whole milliseconds
   * (`Instant.toEpochMilli`), rounding down: 1.5 ms after the epoch gives 1 ms,
   * and 1.5 ms before it gives -2 ms.
   */
  def fromInstant(instant: Instant): Duration =
    Duration(instant.toEpochMilli, TimeUnit.MILLISECONDS)

  /**
   * Returns a duration of `seconds` seconds. A negative value gives a negative
   * duration.
   */
  def fromSeconds(seconds: Long): Duration =
    JavaDuration.ofSeconds(seconds)

  /**
   * Converts a Scala duration. A `FiniteDuration` keeps its length in
   * nanoseconds, negative lengths included. Every other value (`Duration.Inf`,
   * `Duration.MinusInf` and `Duration.Undefined` of
   * `scala.concurrent.duration`) becomes `Duration.Infinity`.
   */
  def fromScala(duration: ScalaDuration): Duration = duration match {
    case d: ScalaFiniteDuration => fromNanos(d.toNanos)
    case _                      => Infinity
  }

  /**
   * Converts a `java.time.Duration` to the range that ZIO uses: a negative
   * duration becomes `Duration.Zero`, a duration of `Duration.Infinity` or
   * longer becomes `Duration.Infinity`, and any other duration keeps its
   * length.
   */
  def fromJava(duration: JavaDuration): Duration =
    if (duration.isNegative) Zero
    else if (duration.compareTo(Infinity) >= 0) Infinity
    else fromNanos(duration.toNanos)

  private def toChronoUnit(unit: TimeUnit): ChronoUnit = unit match {
    case TimeUnit.NANOSECONDS  => ChronoUnit.NANOS
    case TimeUnit.MICROSECONDS => ChronoUnit.MICROS
    case TimeUnit.MILLISECONDS => ChronoUnit.MILLIS
    case TimeUnit.SECONDS      => ChronoUnit.SECONDS
    case TimeUnit.MINUTES      => ChronoUnit.MINUTES
    case TimeUnit.HOURS        => ChronoUnit.HOURS
    case TimeUnit.DAYS         => ChronoUnit.DAYS
  }
}

/**
 * Builds a `Duration` from a whole number of time units: `5.seconds`,
 * `250.millis`, `2.hours`. The `zio` package converts `Int` and `Long` values
 * to it implicitly.
 *
 * A negative number gives a negative duration, and zero gives `Duration.Zero`.
 * Throws `java.lang.ArithmeticException` when the result does not fit in a
 * `java.time.Duration` (for example `Long.MaxValue.days`).
 *
 * @param n
 *   the number of units
 */
final class DurationSyntax(val n: Long) extends AnyVal {
  private[this] def asDuration(unit: TemporalUnit): Duration =
    if (n != 0) JavaDuration.of(n, unit) else Duration.Zero

  /** Returns a duration of `n` nanoseconds. */
  def nanoseconds: Duration = asDuration(ChronoUnit.NANOS)

  /** Returns a duration of `n` nanoseconds; same as `nanoseconds`. */
  def nanos = nanoseconds

  /** Returns a duration of `n` nanoseconds; same as `nanoseconds`. */
  def nanosecond = nanoseconds

  /** Returns a duration of `n` nanoseconds; same as `nanoseconds`. */
  def nano = nanoseconds

  /** Returns a duration of `n` microseconds. */
  def microseconds: Duration = asDuration(ChronoUnit.MICROS)

  /** Returns a duration of `n` microseconds; same as `microseconds`. */
  def micros = microseconds

  /** Returns a duration of `n` microseconds; same as `microseconds`. */
  def microsecond = microseconds

  /** Returns a duration of `n` microseconds; same as `microseconds`. */
  def micro = microseconds

  /** Returns a duration of `n` milliseconds. */
  def milliseconds: Duration = asDuration(ChronoUnit.MILLIS)

  /** Returns a duration of `n` milliseconds; same as `milliseconds`. */
  def millis = milliseconds

  /** Returns a duration of `n` milliseconds; same as `milliseconds`. */
  def millisecond = milliseconds

  /** Returns a duration of `n` milliseconds; same as `milliseconds`. */
  def milli = milliseconds

  /** Returns a duration of `n` seconds. */
  def seconds: Duration = asDuration(ChronoUnit.SECONDS)

  /** Returns a duration of `n` seconds; same as `seconds`. */
  def second = seconds

  /**
   * Returns a duration of `n` minutes. Throws `java.lang.ArithmeticException`
   * when it does not fit in a `java.time.Duration`.
   */
  def minutes: Duration = asDuration(ChronoUnit.MINUTES)

  /** Returns a duration of `n` minutes; same as `minutes`. */
  def minute = minutes

  /**
   * Returns a duration of `n` hours. Throws `java.lang.ArithmeticException`
   * when it does not fit in a `java.time.Duration`.
   */
  def hours: Duration = asDuration(ChronoUnit.HOURS)

  /** Returns a duration of `n` hours; same as `hours`. */
  def hour = hours

  /**
   * Returns a duration of `n` days of exactly 24 hours each. Throws
   * `java.lang.ArithmeticException` when it does not fit in a
   * `java.time.Duration` (for example `Long.MaxValue.days`).
   */
  def days: Duration = asDuration(ChronoUnit.DAYS)

  /** Returns a duration of `n` days; same as `days`. */
  def day = days

}

/**
 * The operators that ZIO adds to `Duration` (an alias for `java.time.Duration`)
 * through an implicit conversion in the `zio` package: arithmetic that
 * saturates at `Duration.Infinity`, comparison, `render`, and conversion to
 * Scala and Java durations.
 *
 * `+`, `*`, `render`, `compare`, `asScala` and `asFiniteDuration` read the
 * length in nanoseconds, so they throw `java.lang.ArithmeticException` when the
 * length does not fit in a `Long` of nanoseconds: longer than
 * `Duration.Infinity` (`Long.MaxValue` nanoseconds), or more than about 292
 * years negative.
 *
 * {{{
 * val backoff: Duration = (200.millis * 1.5) + 1.second
 * val text: String      = backoff.render
 * // "1 s 300 ms"
 * }}}
 */
final class DurationOps(private val duration: Duration) extends AnyVal {

  /**
   * Adds two durations. A negative operand counts as zero, and a sum past
   * `Duration.Infinity` (including any sum with `Duration.Infinity`) gives
   * `Duration.Infinity`.
   */
  def +(other: Duration): Duration = {
    val thisNanos  = if (duration.toNanos > 0) duration.toNanos else 0
    val otherNanos = if (other.toNanos > 0) other.toNanos else 0
    val sum        = thisNanos + otherNanos
    if (sum >= 0) sum.nanos else Duration.Infinity
  }

  /**
   * Multiplies the duration by `factor` in `Double` arithmetic and rounds to
   * the nearest nanosecond, halves up (`3.nanos * 0.5` is 2 ns). Returns
   * `Duration.Zero` when the factor or the duration is zero or negative, and
   * `Duration.Infinity` when the product is longer than `Duration.Infinity` or
   * the factor is `NaN`.
   */
  def *(factor: Double): Duration = {
    val nanos = duration.toNanos
    if (factor <= 0 || nanos <= 0) Duration.Zero
    else if (factor <= Long.MaxValue / nanos.toDouble) (nanos * factor).round.nanoseconds
    else Duration.Infinity
  }

  /** Returns `true` when this duration is longer than or as long as `other`. */
  def >=(other: Duration): Boolean = duration.compareTo(other) >= 0

  /**
   * Returns `true` when this duration is shorter than or as long as `other`.
   */
  def <=(other: Duration): Boolean = duration.compareTo(other) <= 0

  /** Returns `true` when this duration is longer than `other`. */
  def >(other: Duration): Boolean = duration.compareTo(other) > 0

  /** Returns `true` when this duration is shorter than `other`. */
  def <(other: Duration): Boolean = duration.compareTo(other) < 0

  /**
   * Returns `true` when both durations have the same length. Infix `a == b`
   * calls `equals` of `java.time.Duration` instead, which gives the same
   * result.
   */
  def ==(other: Duration): Boolean = duration.compareTo(other) == 0

  /**
   * Returns a short text for the duration, using its largest units:
   *
   *   - under 1 ms: nanoseconds, `"23456 ns"`
   *   - under 1 s: whole milliseconds, `"1 ms"` for 1.5 ms
   *   - under 1 minute: `"2 s 500 ms"`, or `"1 s"` without a millisecond part
   *   - under 1 hour: `"2 m 30 s"`, dropping milliseconds
   *   - longer: days, hours, minutes and seconds, skipping zero parts, as in
   *     `"3 d 2 h 5 m"` and `"1 h"`
   *
   * A negative duration renders as nanoseconds or milliseconds (`(-5).seconds`
   * gives `"-5000 ms"`), and `Duration.Infinity` gives `"106751 d 23 h 47 m 16
   * s"`.
   */
  def render: String = {
    val nanos = duration.toNanos
    TimeUnit.NANOSECONDS.toMillis(nanos) match {
      case 0                       => s"$nanos ns"
      case millis if millis < 1000 => s"$millis ms"
      case millis if millis < 60000 =>
        val maybeMs = Option(millis % 1000).filterNot(_ == 0)
        s"${millis / 1000} s${maybeMs.fold("")(ms => s" $ms ms")}"
      case millis if millis < 3600000 =>
        val maybeSec = Option((millis % 60000) / 1000).filterNot(_ == 0)
        s"${millis / 60000} m${maybeSec.fold("")(s => s" $s s")}"
      case millis =>
        val days    = millis / 86400000
        val hours   = (millis % 86400000) / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000

        List(days, hours, minutes, seconds)
          .zip(List("d", "h", "m", "s"))
          .collect { case (value, unit) if value != 0 => s"$value $unit" }
          .mkString(" ")
    }
  }

  /**
   * Converts to a Scala duration; same as `asFiniteDuration`.
   * `Duration.Infinity` becomes a finite value of `Long.MaxValue` nanoseconds,
   * not `scala.concurrent.duration.Duration.Inf`.
   */
  def asScala: ScalaDuration = asFiniteDuration

  /**
   * Converts to a Scala `FiniteDuration` in nanoseconds. A zero duration
   * becomes `scala.concurrent.duration.Duration.Zero`, and a negative duration
   * stays negative.
   */
  def asFiniteDuration: ScalaFiniteDuration = duration match {
    case Duration.Zero => ScalaDuration.Zero
    case _             => ScalaFiniteDuration(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  /**
   * Returns the duration as a `java.time.Duration`. `Duration` is an alias for
   * that type, so this is the same value.
   */
  def asJava: JavaDuration = duration

  /**
   * Returns the longer of the two durations, or `other` when they are equal.
   */
  def max(other: Duration): Duration = if (duration > other) duration else other

  /**
   * Returns the shorter of the two durations, or `other` when they are equal.
   */
  def min(other: Duration): Duration = if (duration < other) duration else other

  /**
   * Compares the lengths in nanoseconds: returns a negative number, zero or a
   * positive number when this duration is shorter than, as long as, or longer
   * than `other`.
   */
  def compare(other: Duration): Int = duration.toNanos compare other.toNanos

  /**
   * Returns `infinity` when this duration equals `Duration.Infinity`, and
   * `finite(duration)` for any other duration, including one longer than
   * `Duration.Infinity`.
   */
  def fold[Z](infinity: => Z, finite: Duration => Z): Z = duration match {
    case Duration.Infinity => infinity
    case f: Duration       => finite(f)
  }

}
