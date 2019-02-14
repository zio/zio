// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio.clock

import java.util.concurrent.TimeUnit

import scalaz.zio.duration.Duration
import scalaz.zio.scheduler.Scheduler
import scalaz.zio.{ IO, ZIO }

trait Clock extends Scheduler with Serializable {
  val clock: Clock.Interface[Scheduler]
}

object Clock extends Serializable {
  trait Interface[R] extends Serializable {
    def currentTime(unit: TimeUnit): ZIO[R, Nothing, Long]
    val nanoTime: ZIO[R, Nothing, Long]
    def sleep(duration: Duration): ZIO[R, Nothing, Unit]
  }

  trait Live extends Clock {
    object clock extends Interface[Scheduler] {
      def currentTime(unit: TimeUnit): ZIO[Scheduler, Nothing, Long] =
        IO.sync(System.currentTimeMillis).map(l => unit.convert(l, TimeUnit.MILLISECONDS))

      val nanoTime: ZIO[Scheduler, Nothing, Long] = IO.sync(System.nanoTime)

      def sleep(duration: Duration): ZIO[Scheduler, Nothing, Unit] =
        scheduler.scheduler.flatMap(
          scheduler =>
            ZIO.asyncInterrupt[Any, Nothing, Unit] { k =>
              val canceler = scheduler
                .schedule(() => k(ZIO.unit), duration)

              Left(ZIO.sync(canceler()))
            }
        )
    }
  }
}
