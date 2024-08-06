package zio.test

import zio._
import zio.test.Assertion._

import java.util.concurrent.TimeUnit

object TestClockSpecJVM extends ZIOBaseSpec {

  def spec =
    suite("TestClockSpecJVM")(
      suite("asScheduledExecutorService")(
        test("schedules tasks at fixed rate correctly") {
          for {
            runtime                 <- ZIO.runtime[Any]
            ref                     <- Ref.make[List[Long]](List.empty)
            clock                   <- ZIO.clock
            scheduler               <- ZIO.blocking(Clock.scheduler)
            scheduledExecutorService = scheduler.asScheduledExecutorService
            _ <- ZIO.succeed {
                   scheduledExecutorService.scheduleAtFixedRate(
                     new Runnable {
                       def run(): Unit =
                         Unsafe.unsafe { implicit unsafe =>
                           runtime.unsafe.run {
                             clock.sleep(2.seconds) *>
                               clock.currentTime(TimeUnit.SECONDS).flatMap(now => ref.update(now :: _))
                           }.getOrThrowFiberFailure()
                         }
                     },
                     3,
                     5,
                     TimeUnit.SECONDS
                   )
                 }
            _      <- TestClock.adjust(25.seconds)
            values <- ref.get
          } yield assert(values.reverse)(equalTo(List(5L, 10L, 15L, 20L, 25L)))
        },
        test("does not allow tasks to pile up") {
          for {
            runtime                 <- ZIO.runtime[Any]
            ref                     <- Ref.make[List[Long]](List.empty)
            clock                   <- ZIO.clock
            scheduler               <- ZIO.blocking(Clock.scheduler)
            scheduledExecutorService = scheduler.asScheduledExecutorService
            _ <- ZIO.succeed {
                   scheduledExecutorService.scheduleAtFixedRate(
                     new Runnable {
                       def run(): Unit =
                         Unsafe.unsafe { implicit unsafe =>
                           runtime.unsafe.run {
                             clock.sleep(5.seconds) *>
                               clock.currentTime(TimeUnit.SECONDS).flatMap(now => ref.update(now :: _))
                           }.getOrThrowFiberFailure()
                         }
                     },
                     3,
                     2,
                     TimeUnit.SECONDS
                   )
                 }
            _      <- TestClock.adjust(28.seconds)
            values <- ref.get
          } yield assert(values.reverse)(equalTo(List(8L, 13L, 18L, 23L, 28L)))
        },
        test("schedules tasks with fixed delay correctly") {
          for {
            runtime                 <- ZIO.runtime[Any]
            ref                     <- Ref.make[List[Long]](List.empty)
            clock                   <- ZIO.clock
            scheduler               <- ZIO.blocking(Clock.scheduler)
            scheduledExecutorService = scheduler.asScheduledExecutorService
            _ <- ZIO.succeed {
                   scheduledExecutorService.scheduleWithFixedDelay(
                     new Runnable {
                       def run(): Unit =
                         Unsafe.unsafe { implicit unsafe =>
                           runtime.unsafe.run {
                             clock.sleep(2.seconds) *>
                               clock.currentTime(TimeUnit.SECONDS).flatMap(now => ref.update(now :: _))
                           }.getOrThrowFiberFailure()
                         }
                     },
                     3,
                     5,
                     TimeUnit.SECONDS
                   )
                 }
            _      <- TestClock.adjust(33.seconds)
            values <- ref.get
          } yield assert(values.reverse)(equalTo(List(5L, 12L, 19L, 26L, 33L)))
        },
        test("allows scheduled tasks to be interrupted") {
          for {
            runtime                 <- ZIO.runtime[Any]
            ref                     <- Ref.make[List[Long]](List.empty)
            clock                   <- ZIO.clock
            scheduler               <- ZIO.blocking(Clock.scheduler)
            scheduledExecutorService = scheduler.asScheduledExecutorService
            future <- ZIO.logInfo("Scheduling task...") *>
                        ZIO.succeed {
                          scheduledExecutorService.scheduleAtFixedRate(
                            new Runnable {
                              def run(): Unit =
                                Unsafe.unsafe { implicit unsafe =>
                                  runtime.unsafe.run {
                                    ZIO.logInfo("Task started..") *>
                                      clock.sleep(2.seconds) *>
                                      clock.currentTime(TimeUnit.SECONDS).flatMap(now => ref.update(now :: _)) *>
                                      ZIO.logInfo("Task completed")
                                  }.getOrThrowFiberFailure()
                                }
                            },
                            3,
                            5,
                            TimeUnit.SECONDS
                          )
                        }
            _      <- TestClock.adjust(7.seconds)
            _      <- ZIO.succeed(future.cancel(false))
            _      <- TestClock.adjust(11.seconds)
            values <- ref.get
            _      <- ZIO.logInfo(s"Values after interruption: $values")
          } yield assert(values.reverse)(equalTo(List(5L)))
        }
      )
    ) @@ TestAspect.nonFlaky(20)
}
