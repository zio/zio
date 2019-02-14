package scalaz.zio.scheduler

import java.util.concurrent.atomic.AtomicInteger

import scalaz.zio.ZIO
import scalaz.zio.duration.Duration
import scalaz.zio.internal.{ Scheduler => IScheduler }

import scala.scalajs.js

trait SchedulerLive extends Scheduler {
  private[this] val scheduler0 = new IScheduler {
    import IScheduler.CancelToken

    val ConstFalse = () => false

    val _size = new AtomicInteger()

    override def schedule(task: Runnable, duration: Duration): CancelToken = duration match {
      case Duration.Infinity => ConstFalse
      case Duration.Zero =>
        task.run()

        ConstFalse
      case duration: Duration.Finite =>
        _size.incrementAndGet

        val handle = js.timers.setTimeout(duration.toMillis.toDouble) {
          try {
            task.run()
          } finally {
            val _ = _size.decrementAndGet
          }
        }
        () => {
          js.timers.clearTimeout(handle)
          _size.decrementAndGet
          true
        }
    }

    /**
     * The number of tasks scheduled.
     */
    override def size: Int = _size.get()

    /**
     * Initiates shutdown of the scheduler.
     */
    override def shutdown(): Unit = ()
  }

  object scheduler extends Scheduler.Service[Any] {
    val scheduler = ZIO.succeed(scheduler0)
  }
}
object SchedulerLive extends SchedulerLive
