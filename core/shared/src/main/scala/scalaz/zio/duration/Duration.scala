package scalaz.zio.duration

import java.time.{ Duration => JavaDuration }
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

  def asScala: ScalaDuration

  /** The `java.time.Duration` returned for an infinite Duration is technically "only" ~2x10^16 hours long (`Long.MaxValue` number of seconds) */
  def asJava: JavaDuration
}

final object Duration {

  object Finite {

    final def apply(nanos: Long): Duration =
      if (nanos >= 0) new Finite(nanos)
      else Zero

  }

  final case class Finite private (nanos: Long) extends Duration {

    final def +(other: Duration): Duration = other match {
      case Finite(otherNanos) =>
        val sum = nanos + otherNanos
        // Check for overflow
        if (sum >= 0) Finite(sum) else Infinity
      case Infinity => Infinity
    }

    final def *(factor: Double): Duration =
      if (factor < 0) Zero
      else if (factor < 1) Finite((nanos * factor).round)
      else if (factor < Long.MaxValue / nanos) Finite((nanos * factor).round)
      else Infinity

    final def compare(other: Duration) = other match {
      case Finite(otherNanos) => nanos compare otherNanos
      case Infinity           => -1
    }

    final def copy(nanos: Long = this.nanos): Duration = Finite(nanos)

    final def isZero: Boolean = nanos == 0

    final def toMillis: Long = TimeUnit.NANOSECONDS.toMillis(nanos)

    final def toNanos: Long = nanos

    override def asScala: ScalaDuration = ScalaFiniteDuration(nanos, TimeUnit.NANOSECONDS)

    override def asJava: JavaDuration = JavaDuration.ofNanos(nanos)
  }

  final case object Infinity extends Duration {

    final def +(other: Duration): Duration = Infinity

    final def *(factor: Double): Duration =
      if (factor < 0) Duration.Zero else Infinity

    final def compare(other: Duration) = if (other == this) 0 else 1

    val toMillis: Long = TimeUnit.NANOSECONDS.toMillis(Long.MaxValue)

    val toNanos: Long = Long.MaxValue

    val isZero: Boolean = false

    override def asScala: ScalaDuration = ScalaDuration.Inf

    override def asJava: JavaDuration = JavaDuration.ofSeconds(Long.MaxValue)
  }

  final def apply(amount: Long, unit: TimeUnit): Duration = fromNanos(unit.toNanos(amount))

  final def fromNanos(nanos: Long): Duration = Finite(nanos)

  final def fromScala(duration: ScalaDuration): Duration = duration match {
    case d: ScalaFiniteDuration => fromNanos(d.toNanos)
    case _                      => Infinity
  }

  final val Zero: Duration = Finite(0)

}
