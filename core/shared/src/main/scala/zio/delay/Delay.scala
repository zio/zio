package zio.delay

import java.util.concurrent.TimeUnit

import zio.ZIO
import zio.clock._
import zio.duration.Duration
import zio.delay.Delay._

/**
 * Delay represents how much time an effect must wait before start.
 * Delays can be relative to current time (a given duration + current time) or an absolute point in time
 * Delays make waiting less cpu intensive and avoids clock drifts caused by context switching or hibernation
 */
sealed abstract class Delay extends Serializable with Product { self =>

  /**
   * Computes delay related to current time in milliseconds
   */
  final def run: ZIO[Clock, Nothing, Duration] = currentTime(unit = TimeUnit.MILLISECONDS).flatMap { millis =>
    self match {
      case Relative(duration) => ZIO.succeed(duration)
      case Absolute(instant)  => ZIO.succeed(Duration(instant - millis, TimeUnit.MILLISECONDS))
      case Min(l, r)          => l.run.zip(r.run).map { case (d1, d2) => if (d1 < d2) d1 else d2 }
      case Max(l, r)          => l.run.zip(r.run).map { case (d1, d2) => if (d1 > d2) d1 else d2 }
      case Sum(l, r)          => l.run.zip(r.run).map { case (d1, d2) => d1 + d2 }
      case Scale(l, factor)   => l.run.map(d => d * factor)
    }
  }

  /**
   * Multiplies this delay by factor.
   * @see scalaz.zio.duration.Duration.*
   */
  def *(factor: Double) = Scale(self, factor)

  /**
   * Adds given delay to this one
   * @see [[zio.duration.Duration.+]]
   */
  def +(delay: Delay) = Sum(self, delay)

  /**
   * Computes minimum between this and given delay
   * @see [[zio.duration.Duration.min]]
   */
  def min(delay: Delay) = Min(this, delay)

  /**
   * Computes maximum between this and given delay
   * @see [[zio.duration.Duration.min]]
   */
  def max(delay: Delay) = Max(this, delay)
}

object Delay {
  final case class Relative(duration: Duration)    extends Delay
  final case class Absolute(epochInMillis: Long)   extends Delay
  final case class Min(l: Delay, r: Delay)         extends Delay
  final case class Max(l: Delay, r: Delay)         extends Delay
  final case class Scale(l: Delay, factor: Double) extends Delay
  final case class Sum(l: Delay, r: Delay)         extends Delay

  /**
   * Zero delay
   * @see [[zio.duration.Duration.Zero]]
   */
  val none: Delay = Relative(Duration.Zero)

  /**
   * Creates a relative delay based on given Duration
   */
  final def relative(delay: Duration) = Relative(delay)

  /**
   * Creates an absolute delay based on given point.
   */
  final def absolute(epochInMillis: Long) = Absolute(epochInMillis)

}
