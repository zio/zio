package scalaz.zio.testkit

import org.specs2.specification.core.SpecStructure
import scalaz.zio._
import SchedulerSpec._
import scalaz.zio.clock.Clock
import scalaz.zio.duration._

class SchedulerSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  override def is: SpecStructure = "SchedulerSpec".title ^ s2"""
    Scheduled tasks get executed $e1
    Scheduled tasks only get executed when time has passed $e2
    Scheduled tasks can be canceled $e3
    Tasks that are cancelled after completion are not reported as interrupted $e4
    """

  def e1 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        _ <- ZIO.effectTotal(scheduler.schedule(new Runnable {
              override def run(): Unit = unsafeRun(promise.done(ZIO.unit).unit)
            }, 10.seconds))
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        executed <- promise.poll.map(_.nonEmpty)
      } yield executed must beTrue
    )

  def e2 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        _ <- ZIO.effectTotal(scheduler.schedule(new Runnable {
              override def run(): Unit = unsafeRun(promise.done(ZIO.unit).unit)
            }, 10.seconds + 1.nanosecond))
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        executed <- promise.poll.map(_.nonEmpty)
      } yield executed must beFalse
    )

  def e3 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        cancel <- ZIO.effectTotal(scheduler.schedule(new Runnable {
                   override def run(): Unit = unsafeRun(promise.done(ZIO.unit).unit)
                 }, 10.seconds))
        canceled <- ZIO.effectTotal(cancel())
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        executed <- promise.poll.map(_.nonEmpty)
      } yield (!executed && canceled) must beTrue
    )

  def e4 =
    unsafeRun(
      for {
        res       <- mkScheduler(this)
        clock     = res._1
        scheduler <- res._2.scheduler
        promise   <- Promise.make[Nothing, Unit]
        cancel <- ZIO.effectTotal(scheduler.schedule(new Runnable {
                   override def run(): Unit = unsafeRun(promise.done(ZIO.unit).unit)
                 }, 10.seconds))
        _        <- clock.sleep(10.seconds)
        _        <- scheduler.safeShutdown()
        canceled <- ZIO.effectTotal(cancel())
        executed <- promise.poll.map(_.nonEmpty)
      } yield (executed && !canceled) must beTrue
    )

}

object SchedulerSpec {

  def mkScheduler(runtime: Runtime[Clock]): UIO[(TestClock, TestScheduler)] =
    for {
      clockData <- Ref.make(TestClock.Zero)
      clock     = TestClock(clockData)
      scheduler = TestScheduler(clockData, runtime)
    } yield (clock, scheduler)

}
