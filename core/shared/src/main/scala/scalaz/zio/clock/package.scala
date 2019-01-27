package scalaz.zio

import java.util.concurrent.TimeUnit

package object clock {
  final def currentTime(unit: TimeUnit): ZIO[Clock, Nothing, Long] =
    ZIO.readM(_.clock currentTime unit)
  final val nanoTime: ZIO[Clock, Nothing, Long] =
    ZIO.readM(_.clock.nanoTime)
  final def sleep(length: Long, unit: TimeUnit): ZIO[Clock, Nothing, Unit] =
    ZIO.readM(_.clock sleep (length, unit))
}
