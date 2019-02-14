package scalaz.zio

import scalaz.zio.internal.{ Scheduler => IScheduler }

package object scheduler extends Scheduler.Service[Scheduler] {
  final val schedulerService: ZIO[Scheduler, Nothing, Scheduler.Service[Any]] =
    ZIO.access(_.scheduler)

  def scheduler: ZIO[Scheduler, Nothing, IScheduler] =
    ZIO.accessM(_.scheduler.scheduler)
}
