package scalaz.zio

import scalaz.zio.duration.Duration

import java.util.concurrent.TimeUnit

package object clock extends Clock.Interface[Clock] {
  final val clockService: ZIO[Clock, Nothing, Clock.Interface[Any]] =
    ZIO.access(_.clock)

  /**
   * Returns the current time, relative to the Unix epoch.
   */
  final def currentTime(unit: TimeUnit): ZIO[Clock, Nothing, Long] =
    ZIO.accessM(_.clock currentTime unit)

  /**
   * Returns the system nano time, which is not relative to any date.
   */
  final val nanoTime: ZIO[Clock, Nothing, Long] =
    ZIO.accessM(_.clock.nanoTime)

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  final def sleep(duration: Duration): ZIO[Clock, Nothing, Unit] =
    ZIO.accessM(_.clock sleep duration)
}
