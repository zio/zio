package scalaz.zio

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._

object DurationConversions {

  implicit final class DurationInt(private val n: Int) extends AnyVal with DurationConversions {
    override protected def asDuration(unit: TimeUnit): Duration = Duration(n.toLong, unit)
  }

  implicit final class DurationLong(private val n: Long) extends AnyVal with DurationConversions {
    override protected def asDuration(unit: TimeUnit): Duration = Duration(n, unit)
  }

}

trait DurationConversions extends Any {

  protected def asDuration(unit: TimeUnit): Duration

  def ns = asDuration(NANOSECONDS)

  def ms = asDuration(MILLISECONDS)

  def s = asDuration(SECONDS)

}
