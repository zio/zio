/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.duration

import java.time.{ Duration => JavaDuration, Instant }
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration }
import scala.math.Ordered

/** Non-negative duration.
 * Operations that would result in negative value of nanoseconds return zero duration
 * and ones that would result in `Long.MaxValue` overflow return infinity.
 * `Infinity` has `Long.MaxValue` nanoseconds value, but for arithmetic operations behaviour is
 * mathematical infinity like-ish.
 */
sealed trait Duration extends Ordered[Duration] with Serializable with Product {

  /** Adds other `Duration`. When nanoseconds overflow `Long.MaxValue` returns `Infinity`.
   */
  def +(other: Duration): Duration

  /** Multiplies by factor, when nanoseconds overflow `Long.MaxValue` returns `Infinity`,
   * if factor is negative returns `Duration.Zero`.
   */
  def *(factor: Double): Duration

  final def max(other: Duration): Duration = if (this > other) this else other

  final def min(other: Duration): Duration = if (this < other) this else other

  final def fold[Z](infinity: => Z, finite: Duration.Finite => Z): Z = this match {
    case Duration.Infinity  => infinity
    case f: Duration.Finite => finite(f)
  }

  /** Number of milliseconds.*/
  def toMillis: Long

  /** Number of nanoseconds.*/
  def toNanos: Long

  /** Whether this is a zero duration */
  def isZero: Boolean

  type SpecificScalaDuration <: ScalaDuration

  def asScala: SpecificScalaDuration

  /** The `java.time.Duration` returned for an infinite Duration is technically "only" ~2x10^16 hours long (`Long.MaxValue` number of seconds) */
  def asJava: JavaDuration

  def render: String
}

object Duration {
  def fromInterval(start: Instant, end: Instant): Duration = {
    val delta = end.toEpochMilli() - start.toEpochMilli()

    if (delta < 0) Duration.Zero
    else Duration(delta, TimeUnit.MILLISECONDS)
  }

  object Finite {

    def apply(nanos: Long): Finite =
      if (nanos >= 0) new Finite(nanos)
      else Finite(0)

  }

  final case class Finite private (nanos: Long) extends Duration {

    def +(other: Duration): Duration = other match {
      case Finite(otherNanos) =>
        val sum = nanos + otherNanos
        // Check for overflow
        if (sum >= 0) Finite(sum) else Infinity
      case Infinity => Infinity
    }

    def *(factor: Double): Duration =
      if (factor <= 0 || nanos <= 0) Zero
      else if (factor <= Long.MaxValue / nanos.toDouble) Finite((nanos * factor).round)
      else Infinity

    def compare(other: Duration): Int = other match {
      case Finite(otherNanos) => nanos compare otherNanos
      case Infinity           => -1
    }

    def copy(nanos: Long = this.nanos): Duration = Finite(nanos)

    def isZero: Boolean = nanos == 0

    def toMillis: Long = TimeUnit.NANOSECONDS.toMillis(nanos)

    def toNanos: Long = nanos

    override type SpecificScalaDuration = ScalaFiniteDuration

    override def asScala: SpecificScalaDuration = ScalaFiniteDuration(nanos, TimeUnit.NANOSECONDS)

    override def asJava: JavaDuration = JavaDuration.ofNanos(nanos)

    override def render: String =
      toMillis match {
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

  case object Infinity extends Duration {

    def +(other: Duration): Duration = Infinity

    def *(factor: Double): Duration =
      if (factor <= 0) Duration.Zero else Infinity

    def compare(other: Duration): Int = if (other == this) 0 else 1

    val toMillis: Long = TimeUnit.NANOSECONDS.toMillis(Long.MaxValue)

    val toNanos: Long = Long.MaxValue

    val isZero: Boolean = false

    override type SpecificScalaDuration = ScalaDuration.Infinite

    override def asScala: SpecificScalaDuration = ScalaDuration.Inf

    override def asJava: JavaDuration = JavaDuration.ofMillis(Long.MaxValue)

    override def render: String = "Infinity"
  }

  def apply(amount: Long, unit: TimeUnit): Duration = fromNanos(unit.toNanos(amount))

  def fromInstant(instant: Instant): Duration =
    Duration(instant.toEpochMilli, TimeUnit.MILLISECONDS)

  def fromNanos(nanos: Long): Duration = Finite(nanos)

  def fromScala(duration: ScalaDuration): Duration = duration match {
    case d: ScalaFiniteDuration => fromNanos(d.toNanos)
    case _                      => Infinity
  }

  def fromJava(duration: JavaDuration): Duration =
    if (duration.isNegative) Zero
    else if (duration.compareTo(JavaDuration.ofNanos(Long.MaxValue)) >= 0) Infinity
    else fromNanos(duration.toNanos)

  val Zero: Duration = Finite(0)

}
