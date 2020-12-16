package zio.internal

import zio.duration.Duration
import zio.internal.Scheduler.CancelToken

import java.util.concurrent.{ ScheduledExecutorService, TimeUnit }

private[zio] abstract class Scheduler {
  def schedule(task: Runnable, duration: Duration): CancelToken
}

private[zio] object Scheduler {
  type CancelToken = () => Boolean

  def fromScheduledExecutorService(service: ScheduledExecutorService): Scheduler =
    new Scheduler {
      val ConstFalse = () => false

      override def schedule(task: Runnable, duration: Duration): CancelToken = duration match {
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
