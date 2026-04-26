package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration

private[zio] trait ZIOTimeoutOps extends ZIOTimeoutSymmetric { self: ZIO.type =>
  def timeoutToWithClock[R, E, A, B](
    duration: FiniteDuration,
    fallback: ZIO[R, E, B]
  ): ZIO[R with Clock, E, B] => ZIO[R with Clock, E, B] =
    (effect: ZIO[R with Clock, E, A]) =>
      ZIO.asyncInterrupt { cb =>
        val startNanos = System.nanoTime()
        val timer = new AtomicReference[Option[Runnable]](None)

        val schedule = Clock
          .currentScheduler
          .map { scheduler =>
            scheduler.schedule(
              new Runnable {
                def run(): Unit = {
                  if (timer.getAndSet(None).isDefined) {
                    cb(ZIO.succeed(fallback))
                  }
                }
              },
              duration.length,
              duration.unit
            )
          }
          .flatMap { runnable =>
            timer.set(Some(runnable))
            ZIO.succeed(runnable)
          }

        val raceFiber = (schedule raceWith effect.map(ZIO.succeed(_))) {
          case (Exit.Success(timerTask), fiber) =>
            fiber.interruptWith(_.flatMap(_ => timerTask.run()))
          case (Exit.Failure(cause), _) =>
            ZIO.halt(cause)
          case (Exit.Success(fiber), timerTask) =>
            timerTask.getAndSet(None) match {
              case Some(cancel) => cancel.run()
              case None         => ()
            }
            fiber.join
        }.fork

        Left(raceFiber.flatMap(_.interrupt).uninterruptible)
      }.uninterruptible

  def timeoutWithClock[R, E, A](
    duration: FiniteDuration,
    fallback: A
  ): ZIO[R with Clock, E, A] => ZIO[R with Clock, E, A] =
    timeoutToWithClock(duration, ZIO.succeed(fallback))

  def timeoutTOWithClock[R, E, A](
    duration: FiniteDuration
  ): ZIO[R with Clock, E, A] => ZIO[R with Clock, E, Option[A]] =
    timeoutToWithClock(duration, ZIO.none)
}