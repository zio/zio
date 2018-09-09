// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

trait Clock {
  def currentTime(unit: TimeUnit): IO[Nothing, Long]
  val nanoTime: IO[Nothing, Long]
  def sleep(length: Long, unit: TimeUnit): IO[Nothing, Unit]
}

object Clock {
  object Live extends Clock {
    final def currentTime(unit: TimeUnit): IO[Nothing, Long] =
      system.currentTimeMillis.map(l => unit.convert(l, MILLISECONDS))

    final val nanoTime: IO[Nothing, Long] = system.nanoTime

    final def sleep(length: Long, unit: TimeUnit): IO[Nothing, Unit] =
      IO.sleep(FiniteDuration(length, unit))
  }
}
