package scalaz.zio

import scalaz.zio.internal.{ Scheduler => IScheduler }

package object scheduler extends Scheduler.Service[Scheduler] {
  final val schedulerService: ZIO[Scheduler, Nothing, Scheduler.Service[Any]] =
    ZIO.read(_.scheduler)

  def scheduler: ZIO[Scheduler, Nothing, IScheduler] =
    ZIO.readM(_.scheduler.scheduler)
}
