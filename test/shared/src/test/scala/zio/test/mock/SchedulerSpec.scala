package zio.test.mock

import scala.Predef.{ assert => SAssert }

import zio._
import zio.clock.Clock
import zio.duration._

object SchedulerSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(e1, "MockScheduler scheduled tasks get executed")
    SAssert(e2, "MockScheduler scheduled tasks only get executed when time has passed")
    SAssert(e3, "MockScheduler scheduled tasks can be canceled")
    SAssert(e4, "MockScheduler tasks that are cancelled after completion are not reported as interrupted")
  }

  def e1 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        _ <- ZIO.effectTotal(scheduler.schedule(new Runnable {
              override def run(): Unit = unsafeRun(promise.complete(ZIO.unit).unit)
            }, 10.seconds))
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        executed <- promise.poll.map(_.nonEmpty)
      } yield executed
    )

  def e2 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        _ <- ZIO.effectTotal(scheduler.schedule(new Runnable {
              override def run(): Unit = unsafeRun(promise.complete(ZIO.unit).unit)
            }, 10.seconds + 1.nanosecond))
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        executed <- promise.poll.map(_.nonEmpty)
      } yield !executed
    )

  def e3 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        cancel <- ZIO.effectTotal(scheduler.schedule(new Runnable {
                   override def run(): Unit = unsafeRun(promise.complete(ZIO.unit).unit)
                 }, 10.seconds))
        canceled <- ZIO.effectTotal(cancel())
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        executed <- promise.poll.map(_.nonEmpty)
      } yield !executed && canceled
    )

  def e4 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        cancel <- ZIO.effectTotal(scheduler.schedule(new Runnable {
                   override def run(): Unit = unsafeRun(promise.complete(ZIO.unit).unit)
                 }, 10.seconds))
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        canceled <- ZIO.effectTotal(cancel())
        executed <- promise.poll.map(_.nonEmpty)
      } yield executed && !canceled
    )

  def mkScheduler(runtime: Runtime[Clock]): UIO[(MockClock.Mock, MockScheduler)] =
    for {
      clock     <- MockClock.makeMock(MockClock.DefaultData)
      scheduler = MockScheduler(clock.clockState, runtime)
    } yield (clock, scheduler)
}
