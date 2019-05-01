package scalaz.zio.delay

import java.util.concurrent.TimeUnit

import scalaz.zio.ZIO
import scalaz.zio.clock.{ Clock, _ }
import scalaz.zio.delay.Delay._
import scalaz.zio.duration.Duration

/**
 * Delay represents how much time an effect must wait before start.
 * Delays can be relative to current time (a given duration + current time) or an absolute point in time
 * Delays make waiting less cpu intensive and avoids clock drifts caused by context switching or hibernation
 */
sealed trait Delay extends Serializable with Product { self =>

  /**
   * Computes delay related to current time in milliseconds
   */
  final def run: ZIO[Any, Nothing, Duration] = currentTime(unit = TimeUnit.MILLISECONDS).provide(Clock.Live).flatMap {
    millis =>
      self match {
        case Relative(duration) => ZIO.succeed(duration)
        case Absolute(instant)  => ZIO.succeed(Duration(instant - millis, TimeUnit.MILLISECONDS))
        case Min(l, r)          => l.run.zip(r.run).map { case (d1, d2) => if (d1 < d2) d1 else d2 }
        case Max(l, r)          => Min(r, l).run
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
   * @see [[scalaz.zio.duration.Duration.+]]
   */
  def +(delay: Delay) = Sum(self, delay)

  /**
   * Computes minimum between this and given delay
   * @see [[scalaz.zio.duration.Duration.min]]
   */
  def min(delay: Delay) = Min(this, delay)

  /**
   * Computes maximum between this and given delay
   * @see [[scalaz.zio.duration.Duration.min]]
   */
  def max(delay: Delay) = Max(this, delay)
}

object Delay {
  final case class Relative(duration: Duration)    extends Delay
  final case class Absolute(instant: Long)         extends Delay
  final case class Min(l: Delay, r: Delay)         extends Delay
  final case class Max(l: Delay, r: Delay)         extends Delay
  final case class Scale(l: Delay, factor: Double) extends Delay
  final case class Sum(l: Delay, r: Delay)         extends Delay

  /**
   * Zero delay
   * @see [[scalaz.zio.duration.Duration.Zero]]
   */
  val none: Delay = Relative(Duration.Zero)

  /**
   * Creates a relative delay based on given Duration
   */
  final def relative(delay: Duration) = Relative(delay)

  /**
   * Creates an absolute delays based on given epoch in milliseconds
   */
  final def absolute(instant: Long) = Absolute(instant)

}
