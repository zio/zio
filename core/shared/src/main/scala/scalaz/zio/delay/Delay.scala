package scalaz.zio.delay

import java.util.concurrent.TimeUnit

import scalaz.zio.ZIO
import scalaz.zio.clock.{ Clock, _ }
import scalaz.zio.duration.Duration

sealed trait Delay extends Serializable with Product {

  def diff(nanos: Long): Long

  def <(that: Delay): ZIO[Clock, Nothing, Boolean] =
    currentTime(TimeUnit.NANOSECONDS).map(nanos => this.diff(nanos) < that.diff(nanos))

  def <=(that: Delay): ZIO[Clock, Nothing, Boolean] =
    currentTime(TimeUnit.NANOSECONDS).map(nanos => this.diff(nanos) <= that.diff(nanos))

  def >(that: Delay): ZIO[Clock, Nothing, Boolean] = that < this

  def >=(that: Delay): ZIO[Clock, Nothing, Boolean] = that <= this

  def ===(that: Delay): ZIO[Clock, Nothing, Boolean] =
    currentTime(TimeUnit.NANOSECONDS).map(nanos => this.diff(nanos) == that.diff(nanos))

  def *(factor: Double): Delay

  def +(that: Delay): Delay
}

object Delay {
  final case class Relative(duration: Duration) extends Delay {
    override def diff(nanos: Long): Long = duration.toNanos

    override def *(factor: Double): Delay = Relative(duration * factor)

    override def +(that: Delay): Delay = that match {
      case Relative(d) => Relative(duration + d)
      case Absolute(d) => Relative(d + duration)
    }
  }

  final case class Absolute(duration: Duration) extends Delay {
    override def diff(nanos: Long): Long = ???

    override def *(factor: Double): Delay = Absolute(duration * factor)

    override def +(that: Delay): Delay = that match {
      case Relative(d) =>
        Absolute(
          d + Duration.fromNanos(
            Math.max(d.toNanos, duration.toNanos) -
              Math.min(d.toNanos, duration.toNanos)
          )
        )
      case Absolute(d) => Absolute(duration + d)
    }
  }

  final def relative(delay: Duration) = Relative(delay)
}
