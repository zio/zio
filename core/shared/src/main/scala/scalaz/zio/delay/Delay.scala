package scalaz.zio.delay

import java.time.Instant
import java.util.concurrent.TimeUnit

import scalaz.zio.ZIO
import scalaz.zio.clock.Clock
import scalaz.zio.clock._
import scalaz.zio.delay.Delay._
import scalaz.zio.duration.Duration

sealed trait Delay extends Serializable with Product { self =>
  def run: ZIO[Clock, Nothing, Duration] = currentTime(unit = TimeUnit.MILLISECONDS).flatMap { millis =>
    self match {
      case Relative(duration) => ZIO.succeed(duration)
      case Absolute(instant) => ZIO.succeed(Duration(instant.toEpochMilli - millis, TimeUnit.MILLISECONDS))
      case Min(l, r) => l.run.zip(r.run).map{ case (d1, d2) => if (d1 < d2) d1 else d2 }
      case Max(l, r) => Min(r, l).run
      case Sum(l, r) => l.run.zip(r.run).map{ case (d1, d2) => d1 + d2 }
      case TimesFactor(l, factor) => l.run.map(d => d * factor)
    }
  }

  def *(factor: Double) = TimesFactor(self, factor)
  def +(delay: Delay) = Sum(self, delay)
}

object Delay {
  final case class Relative(duration: Duration) extends Delay
  final case class Absolute(instant: Instant) extends Delay
  final case class Min(l: Delay, r: Delay) extends Delay
  final case class Max(l: Delay, r: Delay) extends Delay
  final case class TimesFactor(l: Delay, factor: Double) extends Delay
  final case class Sum(l: Delay, r: Delay) extends Delay

  val none: Delay = Relative(Duration.Zero)
  final def relative(delay: Duration) = Relative(delay)
}