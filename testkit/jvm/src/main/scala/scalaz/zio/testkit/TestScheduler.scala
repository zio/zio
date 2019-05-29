package scalaz.zio.testkit

import scalaz.zio._
import scalaz.zio.clock._
import scalaz.zio.duration.{ Duration, _ }
import scalaz.zio.internal.Scheduler.CancelToken
import scalaz.zio.internal.{ Scheduler => IScheduler }
import scalaz.zio.scheduler.Scheduler
import scalaz.zio.testkit.TestScheduler._

/**
 * Implementation of Scheduler.Service for testing. The passed Ref[TestClock.Data] will be used to determine when
 * to run the scheduled runnables. Make sure to call shutdown() to force execution of all remaining tasks.
 */
final case class TestScheduler(ref: Ref[TestClock.Data], runtime: Runtime[Clock]) extends Scheduler.Service[Any] {

  private[this] val ConstFalse = () => false

  override def scheduler: ZIO[Any, Nothing, TestIScheduler] =
    for {
      tasksRef   <- Ref.make[List[(Long, Promise[Nothing, Unit], Runnable)]](Nil)
      shouldExit <- Ref.make(false)

      runTask = for {
        currentTime <- ref.get.map(_.nanoTime)
        dequeued <- tasksRef.modify { tasks =>
                     tasks.partition(_._1 <= currentTime)
                   }.map(_.map(x => (x._2, x._3)))
        _ <- ZIO.foreach(dequeued) { task =>
              task._1
                .done(ZIO.succeed(()))
                .flatMap { notInterupted =>
                  if (notInterupted) ZIO.effectTotal(task._2.run())
                  else ZIO.unit
                }
                .uninterruptible
            }
      } yield ()
      executor <- runWhile(runTask, shouldExit).provide(runtime.Environment).fork

      scheduler = new TestIScheduler {
        override def schedule(task: Runnable, duration: Duration): CancelToken =
          duration match {
            case Duration.Infinity =>
              ConstFalse
            case Duration.Zero =>
              task.run()
              ConstFalse
            case duration: Duration.Finite =>
              val promise = runtime.unsafeRun(for {
                currentTime <- ref.get.map(_.nanoTime)
                targetTime  = currentTime + duration.toNanos
                promise     <- Promise.make[Nothing, Unit]
                _           <- tasksRef.update(tasks => (targetTime, promise, task) :: tasks)
              } yield promise)
              () => runtime.unsafeRun(promise.done(ZIO.succeed(())))
          }

        override def size: Int =
          runtime.unsafeRun(tasksRef.get.map(_.size))

        override def safeShutdown(): UIO[Unit] =
          shouldExit.update(_ => true) *> executor.join

        override def shutdown(): Unit =
          runtime.unsafeRun(safeShutdown())

      }
    } yield scheduler
}

object TestScheduler {

  trait TestIScheduler extends IScheduler {

    def safeShutdown(): UIO[Unit]

  }

  private[TestScheduler] def runWhile(
    task: UIO[Unit],
    ref: Ref[Boolean],
    pause: Duration = 10.milliseconds
  ): ZIO[Clock, Nothing, Unit] =
    (task *> ref.get).flatMap { exit =>
      if (exit) task.unit // make sure everything is finished
      else sleep(pause) *> runWhile(task, ref, pause)
    }

}
