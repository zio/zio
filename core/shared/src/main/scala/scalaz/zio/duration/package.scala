package scalaz.zio

import java.util.concurrent.TimeUnit

package object duration {

  implicit final class DurationInt(private val n: Int) extends AnyVal with DurationConversions {
    override protected def asDuration(unit: TimeUnit): Duration = Duration(n.toLong, unit)
  }

  implicit final class DurationLong(private val n: Long) extends AnyVal with DurationConversions {
    override protected def asDuration(unit: TimeUnit): Duration = Duration(n, unit)
  }

}