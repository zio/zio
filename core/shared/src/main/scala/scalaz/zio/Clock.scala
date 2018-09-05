// Copyright (C) 2018 John A. De Goes. All rights reserved.

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scalaz.zio._



trait Clock {
  def currentTime(unit: TimeUnit): IO[Nothing, Long]
  def nanoTime: IO[Nothing, Long]
  def sleep(length: Long, unit: TimeUnit): IO[Nothing, Unit]
}

object Clock {

  object Live extends Clock {
    def currentTime(unit: TimeUnit): IO[Nothing, Long] = IO.sync(unit.convert(System.nanoTime(), NANOSECONDS))

    def nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime())

    def sleep(length: Long, unit: TimeUnit): IO[Nothing, Unit] = IO.sleep(FiniteDuration(length, unit))
  }

}
