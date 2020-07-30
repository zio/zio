package zio.internal

import java.time.Duration
import java.util.concurrent.{ ScheduledExecutorService, TimeUnit }

import zio.internal.Scheduler.CancelToken

private[zio] trait Scheduler {
  def schedule(task: Runnable, duration: Duration): CancelToken
}

private[zio] object Scheduler {
  type CancelToken = () => Boolean

  def fromScheduledExecutorService(service: ScheduledExecutorService): Scheduler =
    new Scheduler {
      val ConstFalse = () => false

      override def schedule(task: Runnable, duration: Duration): CancelToken = {
        val nanos = duration.toNanos

        if (nanos <= 0) {
          task.run()

          ConstFalse
        } else {
          nanos match {
            case zio.duration.infiniteNano => ConstFalse
            case nanos =>
              val future = service.schedule(new Runnable {
                def run: Unit =
                  task.run()
              }, nanos, TimeUnit.NANOSECONDS)

              () => future.cancel(true)
          }
        }
      }

    }
}
