package zio.internal

import zio.duration.Duration
import zio.internal.Timer.CancelToken

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

abstract class Timer extends TimerPlatformSpecific { self =>
  def schedule(task: Runnable, duration: Duration): CancelToken
}

object Timer {
  type CancelToken = () => Boolean

  def fromScheduledExecutorService(service: ScheduledExecutorService): Timer =
    new Timer {
      val ConstFalse = () => false

      override def schedule(task: Runnable, duration: Duration): CancelToken = (duration: @unchecked) match {
        case Duration.Infinity => ConstFalse
        case Duration.Zero =>
          task.run()

          ConstFalse
        case Duration.Finite(_) =>
          val future = service.schedule(
            new Runnable {
              def run: Unit =
                task.run()
            },
            duration.toNanos,
            TimeUnit.NANOSECONDS
          )

          () => future.cancel(true)
      }
    }
}
