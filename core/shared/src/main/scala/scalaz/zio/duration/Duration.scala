package scalaz.zio.duration

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{ Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration }
import scala.math.Ordered

sealed trait Duration extends Ordered[Duration] with Serializable with Product {

  def +(other: Duration): Duration

  def *(factor: Double): Duration

  final def max(other: Duration): Duration = if (this > other) this else other

  final def min(other: Duration): Duration = if (this < other) this else other

  final def fold[Z](infinity: => Z, finite: Duration.Finite => Z): Z = this match {
    case Duration.Infinity  => infinity
    case f: Duration.Finite => finite(f)
  }

}

final object Duration {

  object Finite {

    final def apply(nanos: Long): Duration =
      if (nanos >= 0) new Finite(nanos)
      else Infinity

  }

  final case class Finite private (nanos: Long) extends Duration {

    final def +(other: Duration): Duration = other match {
      case Finite(otherNanos) => Finite(nanos + otherNanos)
      case Infinity           => Infinity
    }

    final def *(factor: Double): Duration =
      if (!factor.isInfinite && !factor.isNaN) Finite((nanos * factor).round)
      else Infinity

    final def compare(other: Duration) = other match {
      case Finite(otherNanos) => nanos compare otherNanos
      case Infinity           => -1
    }

    final def copy(nanos: Long = this.nanos): Duration = Finite(nanos)

    final def isZero: Boolean = nanos == 0

    final def toMillis: Long = TimeUnit.NANOSECONDS.toMillis(nanos)

    final def toNanos: Long = nanos
  }

  final case object Infinity extends Duration {

    final def +(other: Duration): Duration = Infinity

    final def *(factor: Double): Duration = Infinity

    final def compare(other: Duration) = if (other == this) 0 else 1

  }

  final def apply(amount: Long, unit: TimeUnit): Duration = fromNanos(unit.toNanos(amount))

  final def fromNanos(nanos: Long): Duration = Finite(nanos)

  final def fromScalaDuration(duration: ScalaDuration): Duration = duration match {
    case d: ScalaFiniteDuration => fromNanos(d.toNanos)
    case _                      => Infinity
  }

  final val Zero: Duration = Finite(0)

}
