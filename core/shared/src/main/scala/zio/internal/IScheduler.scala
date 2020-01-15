package zio.internal

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import zio.duration.Duration
import zio.internal.IScheduler.CancelToken

private[zio] trait IScheduler {
  def schedule(task: Runnable, duration: Duration): CancelToken
}

private[zio] object IScheduler {
  type CancelToken = () => Boolean

  def fromScheduledExecutorService(service: ScheduledExecutorService): IScheduler =
    new IScheduler {
      val ConstFalse = () => false

      override def schedule(task: Runnable, duration: Duration): CancelToken = duration match {
        case Duration.Infinity => ConstFalse
        case Duration.Zero =>
          task.run()

          ConstFalse
        case duration: Duration.Finite =>
          val future = service.schedule(new Runnable {
            def run: Unit =
              task.run()
          }, duration.toNanos, TimeUnit.NANOSECONDS)

          () => future.cancel(true)
      }
    }
}
