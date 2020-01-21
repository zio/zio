package zio.test.environment

import zio.duration._
import zio.scheduler.Scheduler
import zio.scheduler.scheduler
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock._
import zio.{ Fiber, Promise, UIO }

object SchedulerSpec extends ZIOBaseSpec {

  def spec = suite("SchedulerSpec")(
    testM("scheduled tasks get executed")(
      for {
        scheduler <- scheduler
        promise   <- Promise.make[Nothing, Unit]
        _         <- runTask(scheduler, promise, 10.seconds)
        _         <- TestClock.adjust(10.seconds)
        _         <- promise.await
      } yield assertCompletes
    ),
    testM("scheduled tasks only get executed when time has passed")(
      for {
        scheduler <- scheduler
        promise   <- Promise.make[Nothing, Unit]
        _         <- runTask(scheduler, promise, 10.seconds + 1.nanosecond)
        _         <- adjust(10.seconds)
        executed  <- promise.poll.map(_.nonEmpty)
      } yield assert(executed)(isFalse)
    ),
    testM("scheduled tasks can be canceled")(
      for {
        scheduler <- scheduler
        promise   <- Promise.make[Nothing, Unit]
        fiber     <- runTask(scheduler, promise, 10.seconds + 1.nanosecond)
        exit      <- fiber.interrupt
        _         <- adjust(10.seconds)
        executed  <- promise.poll.map(_.nonEmpty)
      } yield {
        assert(executed)(isFalse) &&
        assert(exit.interrupted)(isTrue)
      }
    ),
    testM("tasks that are cancelled after completion are not reported as interrupted")(
      for {
        scheduler <- scheduler
        promise   <- Promise.make[Nothing, Unit]
        fiber     <- runTask(scheduler, promise, 10.seconds)
        _         <- adjust(10.seconds + 1.nanos)
        _         <- promise.await
        exit      <- fiber.interrupt
      } yield assert(exit.interrupted)(isFalse)
    )
  )

  def runTask(
    scheduler: Scheduler.Service,
    promise: Promise[Nothing, Unit],
    duration: Duration
  ): UIO[Fiber[Nothing, Unit]] =
    scheduler.schedule(promise.succeed(()), duration).unit.fork
}
