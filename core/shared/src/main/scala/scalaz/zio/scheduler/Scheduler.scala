package scalaz.zio.scheduler

import scalaz.zio.ZIO
import scalaz.zio.internal.{ Scheduler => IScheduler }

trait Scheduler extends Serializable {
  val scheduler: Scheduler.Service[Any]
}

object Scheduler extends Serializable {
  trait Service[R] extends Serializable {
    def scheduler: ZIO[R, Nothing, IScheduler]
  }
}
