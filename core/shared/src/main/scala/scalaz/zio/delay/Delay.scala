package scalaz.zio.delay

import scalaz.zio.ZIO
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration
import scalaz.zio.clock._

sealed trait Delay extends Serializable with Product {

  def compareTo(that: Delay): Int
  def eq(that: Delay): Boolean

  def <(that: Delay): ZIO[Clock, Nothing, Boolean]   = clockService.map(_ => (this compareTo that) < 0)
  def <=(that: Delay): ZIO[Clock, Nothing, Boolean]  = clockService.map(_ => (this compareTo that) <= 0)
  def >(that: Delay): ZIO[Clock, Nothing, Boolean]   = clockService.map(_ => (this compareTo that) > 0)
  def >=(that: Delay): ZIO[Clock, Nothing, Boolean]  = clockService.map(_ => (this compareTo that) >= 0)
  def ===(that: Delay): ZIO[Clock, Nothing, Boolean] = clockService.map(_ => this eq that)

}

object Delay {
  case class Relative(duration: Duration) extends Delay {
    override def compareTo(that: Delay): Int = that match {
      case Relative(d)      => duration compareTo d
      case Absolute(millis) => duration.toMillis compareTo millis
    }

    override def eq(that: Delay): Boolean = that match {
      case Relative(d) => d == duration
      case _           => false
    }
  }

  case class Absolute(millis: Long) extends Delay {
    override def compareTo(that: Delay): Int = that match {
      case Relative(d)  => millis compareTo d.toMillis
      case Absolute(ml) => millis compareTo ml
    }

    override def eq(that: Delay): Boolean = that match {
      case Absolute(ml) => ml == millis
      case _            => false
    }
  }

  final def relative(delay: Duration) = Relative(delay)
}
