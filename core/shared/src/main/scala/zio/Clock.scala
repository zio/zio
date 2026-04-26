package zio

import zio.internal.Executor

trait Clock extends Serializable {
  def currentDateTime: UIO[DateTime]
  def currentTime(unit: TimeUnit): UIO[Long]
  def sleep(duration: Duration): UIO[Unit]

  def scheduler: UIO[ZScheduler]
}

object Clock extends Serializable {
  type Service = Clock

  trait Live extends Clock {
    self: FiberRuntime[_] =>

    final def currentDateTime: UIO[DateTime] =
      currentTime(TimeUnit.MILLISECONDS).map(DateTime.fromMillis)

    final def currentTime(unit: TimeUnit): UIO[Long] =
      ZIO.succeed(unit.convert(System.nanoTime(), TimeUnit.NANOSECONDS))

    final def sleep(duration: Duration): UIO[Unit] =
      ZIO.asyncInterrupt { cb =>
        if (duration.isFinite) {
          val nanos = duration.toNanos
          val start = System.nanoTime()
          val timer = new AtomicReference[Option[Runnable]](None)

          val schedule = currentScheduler.map { scheduler =>
            scheduler.schedule(
              new Runnable {
                def run(): Unit = {
                  if (timer.getAndSet(None).isDefined) {
                    cb(ZIO.unit)
                  }
                }
              },
              nanos,
              TimeUnit.NANOSECONDS
            )
          }.flatMap { runnable =>
            timer.set(Some(runnable))
            ZIO.succeed(runnable)
          }

          val fiber = schedule.fork
          Left(fiber.flatMap(_.interrupt).uninterruptible)
        } else {
          cb(ZIO.unit)
          Left(ZIO.unit)
        }
      }.uninterruptible

    final def scheduler: UIO[ZScheduler] =
      ZIO.succeed(new ZScheduler {
        def schedule(task: Runnable, delay: Long, unit: TimeUnit): Runnable = {
          val nanos = unit.toNanos(delay)
          val fiber = self.scheduleTask(task, nanos)
          () => fiber.cancel()
        }
      })
  }
}