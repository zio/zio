package scalaz.zio

import scalaz.zio.internal.{ Scheduler => IScheduler }

package object scheduler extends Scheduler.Service[Scheduler] {
  def scheduler: ZIO[Scheduler, Nothing, IScheduler] =
    ZIO.readM(_.scheduler.scheduler)
}
