package zio

import zio.internal.NIOExecutor
import zio.test.Assertion._
import zio.test._

object NIOClockSpec extends ZIOSpecDefault {

  /**
   * Overriding the bootstrap layer allows to replace the default services (like
   * Clock) for all tests within this spec.
   */
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] = {
    val customClockLayer: ZLayer[Any, Config.Error, Clock] =
      NIOExecutor.live >>> NIOClock.live

    val liveServices: ZLayer[Any, Nothing, Console with System with Random] =
      ZLayer.succeed(Console.ConsoleLive) ++
        ZLayer.succeed(System.SystemLive) ++
        ZLayer.succeed(Random.RandomLive)

    val customLiveEnvironment = (customClockLayer ++ liveServices).orDie

    val testFiberRefGen: ULayer[Unit] =
      ZLayer.scoped(FiberRef.currentFiberIdGenerator.locallyScoped(FiberId.Gen.Monotonic)(Trace.empty))

    customLiveEnvironment >>> (TestEnvironment.live ++ testFiberRefGen)
  }

  def spec = suite("NIOClockSpec")(
    test("sleep should wait for the specified duration") {
      // Use Live.live to ensure ZIO.sleep uses the real clock.
      Live.live {
        for {
          start <- Clock.nanoTime
          _     <- ZIO.sleep(100.millis)
          end   <- Clock.nanoTime
          delta  = Duration.fromNanos(end - start)
        } yield assertTrue(delta >= 100.millis)
      }
    },
    test("interrupting a sleep should complete immediately") {
      // Use Live.live to ensure ZIO.sleep uses the real clock.
      Live.live {
        for {
          fiber <- ZIO.sleep(1.minute).fork
          _     <- ZIO.yieldNow // Ensure the fiber has a chance to start sleeping.
          exit  <- fiber.interrupt
        } yield assert(exit)(isInterrupted)
      }
    },
    test("the provided scheduler should schedule a task to run") {
      for {
        promise   <- Promise.make[Nothing, Unit]
        scheduler <- Clock.scheduler
        _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
               scheduler.schedule(
                 () => { Runtime.default.unsafe.run(promise.succeed(())); () },
                 50.millis
               )
             })
        isDoneBefore <- promise.isDone
        _            <- TestClock.adjust(50.millis)
        isDoneAfter  <- promise.isDone
      } yield assertTrue(!isDoneBefore) && assertTrue(isDoneAfter)
    }
  )
}
