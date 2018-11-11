package scalaz.zio

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{ Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration }
import scala.math.Ordered

sealed trait Duration extends Ordered[Duration] {

  def +(other: Duration): Duration

  def *(factor: Double): Duration

  def max(other: Duration): Duration = if (this > other) this else other

  def min(other: Duration): Duration = if (this < other) this else other

}

final object Duration {

  object Finite {

    def apply(nanos: Long): Duration =
      if (nanos >= 0) new Finite(nanos)
      else Infinity

  }

  final case class Finite private (nanos: Long) extends Duration {

    def +(other: Duration): Duration = other match {
      case Finite(otherNanos) => new Finite(nanos + otherNanos)
      case Infinity           => Infinity
    }

    def *(factor: Double): Duration =
      if (!factor.isInfinite && !factor.isNaN) Finite((nanos * factor).round)
      else Infinity

    def compare(other: Duration) = other match {
      case Finite(otherNanos) => nanos compare otherNanos
      case Infinity           => -1
    }

    def copy(nanos: Long = this.nanos): Duration = Finite(nanos)

    def toMillis: Long = TimeUnit.NANOSECONDS.toMillis(nanos)

    def toNanos: Long = nanos
  }

  final case object Infinity extends Duration {

    def +(other: Duration): Duration = Infinity

    def *(factor: Double): Duration = Infinity

    def compare(other: Duration) = if (other == this) 0 else 1

  }

  def apply(amount: Long, unit: TimeUnit): Duration = apply(unit.toNanos(amount))

  def apply(nanos: Long): Duration = Finite(nanos)

  def fromScalaDuration(duration: ScalaDuration): Duration = duration match {
    case d: ScalaFiniteDuration => apply(d.toNanos)
    case _                      => Infinity
  }

  val Zero: Duration = Finite(0)

}
