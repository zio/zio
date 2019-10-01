package zio.test.mock

import zio._
import zio.duration._
import zio.internal.{ Scheduler => IScheduler }
import zio.internal.Scheduler.CancelToken
import zio.test.Async
import zio.test.TestUtils.label
import zio.test.ZIOBaseSpec

object SchedulerSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(e1, "scheduled tasks get executed"),
    label(e2, "scheduled tasks only get executed when time has passed"),
    label(e3, "scheduled tasks can be canceled"),
    label(e4, "tasks that are cancelled after completion are not reported as interrupted"),
    label(e5, "scheduled tesks get executed before shutdown")
  )

  def e1 =
    unsafeRunToFuture(
      for {
        clock     <- MockClock.makeMock(MockClock.DefaultData)
        scheduler <- clock.scheduler
        promise   <- Promise.make[Nothing, Unit]
        _         <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds))
        _         <- clock.adjust(10.seconds)
        _         <- promise.await
      } yield true
    )

  def e2 =
    unsafeRunToFuture(
      for {
        clock     <- MockClock.makeMock(MockClock.DefaultData)
        scheduler <- clock.scheduler
        promise   <- Promise.make[Nothing, Unit]
        _         <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds + 1.nanosecond))
        _         <- clock.adjust(10.seconds)
        executed  <- promise.poll.map(_.nonEmpty)
      } yield !executed
    )

  def e3 =
    unsafeRunToFuture(
      for {
        clock     <- MockClock.makeMock(MockClock.DefaultData)
        scheduler <- clock.scheduler
        promise   <- Promise.make[Nothing, Unit]
        cancel    <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds))
        canceled  <- ZIO.effectTotal(cancel())
        _         <- clock.adjust(10.seconds)
        executed  <- promise.poll.map(_.nonEmpty)
      } yield !executed && canceled
    )

  def e4 =
    unsafeRunToFuture(
      for {
        clock     <- MockClock.makeMock(MockClock.DefaultData)
        scheduler <- clock.scheduler
        promise   <- Promise.make[Nothing, Unit]
        cancel    <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds))
        _         <- clock.adjust(10.seconds)
        _         <- promise.await
        canceled  <- ZIO.effectTotal(cancel())
      } yield !canceled
    )

  def e5 =
    unsafeRunToFuture(
      for {
        clock     <- MockClock.makeMock(MockClock.DefaultData)
        scheduler <- clock.scheduler
        promise   <- Promise.make[Nothing, Unit]
        _         <- ZIO.effectTotal(runTask(scheduler, promise, 10.seconds))
        _         <- ZIO.effectTotal(scheduler.shutdown())
        _         <- promise.await
        time      <- clock.nanoTime
      } yield time == 10000000000L
    )

  private def runTask(scheduler: IScheduler, promise: Promise[Nothing, Unit], duration: Duration): CancelToken =
    scheduler.schedule(
      new Runnable {
        override def run(): Unit = {
          val _ = unsafeRunToFuture(promise.succeed(()))
          ()
        }
      },
      duration
    )
}
