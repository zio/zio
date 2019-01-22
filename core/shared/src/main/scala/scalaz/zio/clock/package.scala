package scalaz.zio

import java.util.concurrent.TimeUnit

package object clock {
  def currentTime(unit: TimeUnit): ZIO[Clock, Nothing, Long] =
    ZIO.readM(_.clock currentTime unit)
  val nanoTime: ZIO[Clock, Nothing, Long] =
    ZIO.readM(_.clock.nanoTime)
  def sleep(length: Long, unit: TimeUnit): ZIO[Clock, Nothing, Unit] =
    ZIO.readM(_.clock sleep(length, unit))
}
