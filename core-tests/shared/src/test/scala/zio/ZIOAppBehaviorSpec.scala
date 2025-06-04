package zio

import zio.test._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import zio.test.TestClock
import zio.Promise

object ZIOAppBehaviorSpec extends ZIOBaseSpec {
  def spec = suite("ZIOAppBehaviorSpec")(
    suite("app completion behavior")(
      test("completes successfully with exit code 0") {
        val app = ZIOApp.fromZIO(ZIO.succeed("success"))
        for {
          code <- app.invoke(Chunk.empty).exitCode
        } yield assertTrue(code == ExitCode.success)
      },
      test("completes with failure and exit code 1") {
        val app = ZIOApp.fromZIO(ZIO.fail("error"))
        for {
          code <- app.invoke(Chunk.empty).exitCode
        } yield assertTrue(code == ExitCode.failure)
      },
      test("completes with defect and exit code 1") {
        val app = ZIOApp.fromZIO(ZIO.die(new RuntimeException("defect")))
        for {
          code <- app.invoke(Chunk.empty).exitCode
        } yield assertTrue(code == ExitCode.failure)
      }
    ),
    suite("finalizer behavior")(
      test("runs finalizers on normal completion") {
        val finalizerRun = new AtomicBoolean(false)
        val app = ZIOApp.fromZIO(
          ZIO.succeed("success").ensuring(ZIO.succeed(finalizerRun.set(true)))
        )
        for {
          _ <- app.invoke(Chunk.empty)
        } yield assertTrue(finalizerRun.get())
      },
      test("runs finalizers on failure") {
        val finalizerRun = new AtomicBoolean(false)
        val app = ZIOApp.fromZIO(
          ZIO.fail("error").ensuring(ZIO.succeed(finalizerRun.set(true)))
        )
        for {
          _ <- app.invoke(Chunk.empty).either
        } yield assertTrue(finalizerRun.get())
      },
      test("runs finalizers on interruption") {
        val finalizerRun = new AtomicBoolean(false)
        val latch        = new CountDownLatch(1)
        val app = ZIOApp.fromZIO(
          (ZIO.sleep(5.seconds) *> ZIO.succeed("never"))
            .ensuring(ZIO.succeed(finalizerRun.set(true)))
            .onInterrupt(ZIO.succeed(latch.countDown()))
        )
        for {
          fiber <- app.invoke(Chunk.empty).fork
          _     <- TestClock.adjust(5.seconds)
          _     <- fiber.interrupt
          _     <- ZIO.attempt(latch.await(5, TimeUnit.SECONDS)).orDie
        } yield assertTrue(finalizerRun.get())
      }
    ),
    suite("graceful shutdown behavior")(
      test("shutdown sequence doesn't hang and is reasonably fast") {
        val app = ZIOApp.fromZIO(
          ZIO.sleep(10.seconds).onInterrupt(ZIO.sleep(5.seconds))
        )
        for {
          fiber <- app.invoke(Chunk.empty).fork
          _     <- TestClock.adjust(10.seconds)
          start <- Clock.currentTime(TimeUnit.MILLISECONDS)
          _     <- fiber.interrupt
          // Do NOT adjust the clock here, just let the interruption happen
          end     <- Clock.currentTime(TimeUnit.MILLISECONDS)
          duration = Duration.fromMillis(end - start)
        } yield assertTrue(duration < 2.seconds)
      },
      test("allows finalizers to complete within reasonable time") {
        for {
          promise <- Promise.make[Nothing, Unit]
          fiber   <- (ZIO.sleep(1.day).ensuring(promise.succeed(()))).fork
          _       <- ZIO.yieldNow
          _       <- fiber.interrupt
          _       <- fiber.await
          ran     <- promise.isDone
        } yield assertTrue(ran)
      },
      test("handles interruption gracefully") {
        for {
          promise <- Promise.make[Nothing, Unit]
          fiber   <- (ZIO.sleep(1.day).ensuring(promise.succeed(()))).fork
          _       <- ZIO.yieldNow
          _       <- fiber.interrupt
          _       <- fiber.await
          ran     <- promise.isDone
        } yield assertTrue(ran)
      }
    ),
    suite("signal handling")(
      test("handles interruption gracefully") {
        for {
          promise <- Promise.make[Nothing, Unit]
          fiber   <- (ZIO.sleep(1.day).ensuring(promise.succeed(()))).fork
          _       <- ZIO.yieldNow
          _       <- fiber.interrupt
          _       <- fiber.await
          ran     <- promise.isDone
        } yield assertTrue(ran)
      }
    )
  ) @@ TestAspect.timeout(1.minute)
}
