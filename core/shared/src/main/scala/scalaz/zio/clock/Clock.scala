// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio.clock

import java.util.concurrent.TimeUnit

import scalaz.zio.duration.Duration
import scalaz.zio.{ system, IO }

trait Clock extends Serializable {
  val clock: Clock.Interface
}

object Clock extends Serializable {
  trait Interface {
    def currentTime(unit: TimeUnit): IO[Nothing, Long]
    val nanoTime: IO[Nothing, Long]
    def sleep(length: Long, unit: TimeUnit): IO[Nothing, Unit]
  }

  trait Live extends Clock {
    object clock extends Interface {
      final def currentTime(unit: TimeUnit): IO[Nothing, Long] =
        system.currentTimeMillis.map(l => unit.convert(l, TimeUnit.MILLISECONDS))

      final val nanoTime: IO[Nothing, Long] = system.nanoTime

      final def sleep(length: Long, unit: TimeUnit): IO[Nothing, Unit] =
        IO.sleep(Duration(length, unit))
    }
  }
  object Live extends Live

}
