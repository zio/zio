package zio.stream

import zio._
import zio.concurrent.CountdownLatch
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

/**
 * Tests to verify that parallelism in mapZIOPar is no longer bounded by buffer
 * size. This addresses issue #9339.
 */
object ZStreamMapZIOParParallelismSpec extends ZIOBaseSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZStream#mapZIOPar parallelism tests")(
      test("parallelism greater than default buffer size (16) is honored") {
        // This test verifies that we can achieve parallelism > 16 (the old default buffer size)
        val parallelism = 32
        for {
          latch <- CountdownLatch.make(parallelism + 1)
          fiber <- ZStream
                     .range(0, 100)
                     .mapZIOPar(parallelism)(_ => latch.countDown *> latch.await)
                     .runDrain
                     .fork
          // Wait until exactly parallelism fibers are running (all countdown, leaving 1)
          _     <- Live.live(latch.count.delay(100.micros)).repeatUntil(_ == 1)
          _     <- latch.countDown
          count <- latch.count
          _     <- fiber.join
        } yield assertTrue(count == 0)
      } @@ jvmOnly @@ nonFlaky,
      test("parallelism of 64 works correctly") {
        // This test ensures that higher parallelism levels work
        val parallelism = 64
        for {
          latch <- CountdownLatch.make(parallelism + 1)
          fiber <- ZStream
                     .range(0, 200)
                     .mapZIOPar(parallelism)(_ => latch.countDown *> latch.await)
                     .runDrain
                     .fork
          _     <- Live.live(latch.count.delay(100.micros)).repeatUntil(_ == 1)
          _     <- latch.countDown
          count <- latch.count
          _     <- fiber.join
        } yield assertTrue(count == 0)
      } @@ jvmOnly @@ nonFlaky,
      test("parallelism with explicit small buffer size still honors parallelism") {
        // Even with explicit bufferSize parameter, parallelism should be honored
        val parallelism = 32
        val bufferSize  = 8 // Smaller than parallelism
        for {
          latch <- CountdownLatch.make(parallelism + 1)
          fiber <- ZStream
                     .range(0, 100)
                     .mapZIOPar(parallelism, bufferSize)(_ => latch.countDown *> latch.await)
                     .runDrain
                     .fork
          _     <- Live.live(latch.count.delay(100.micros)).repeatUntil(_ == 1)
          _     <- latch.countDown
          count <- latch.count
          _     <- fiber.join
        } yield assertTrue(count == 0)
      } @@ jvmOnly @@ nonFlaky,
      test("very large parallelism value doesn't cause OOM") {
        // Test that Int.MaxValue or very large values don't cause memory issues
        // We limit the stream size to ensure we don't actually try to run Int.MaxValue operations
        assertZIO(
          ZStream
            .range(0, 100)
            .mapZIOPar(Int.MaxValue)(i => ZIO.succeed(i * 2))
            .runCollect
        )(hasSize(equalTo(100)))
      },
      test("ordering is preserved with parallelism > 16") {
        // Verify that ordering guarantees are maintained
        val parallelism = 32
        val count       = 100
        for {
          result <- ZStream
                      .range(0, count)
                      .mapZIOPar(parallelism)(i => ZIO.succeed(i))
                      .runCollect
        } yield assertTrue(result == Chunk.fromIterable(0 until count))
      },
      test("mapZIOParUnordered honors parallelism greater than buffer size") {
        // Test the unordered variant as well
        val parallelism = 32
        for {
          latch <- CountdownLatch.make(parallelism + 1)
          fiber <- ZStream
                     .range(0, 100)
                     .mapZIOParUnordered(parallelism)(_ => latch.countDown *> latch.await)
                     .runDrain
                     .fork
          _     <- Live.live(latch.count.delay(100.micros)).repeatUntil(_ == 1)
          _     <- latch.countDown
          count <- latch.count
          _     <- fiber.join
        } yield assertTrue(count == 0)
      } @@ jvmOnly @@ nonFlaky,
      test("old default behavior still works (parallelism <= 16)") {
        // Ensure that existing code with parallelism <= 16 continues to work
        val parallelism = 8
        for {
          latch <- CountdownLatch.make(parallelism + 1)
          fiber <- ZStream
                     .range(0, 50)
                     .mapZIOPar(parallelism)(_ => latch.countDown *> latch.await)
                     .runDrain
                     .fork
          _     <- Live.live(latch.count.delay(100.micros)).repeatUntil(_ == 1)
          _     <- latch.countDown
          count <- latch.count
          _     <- fiber.join
        } yield assertTrue(count == 0)
      } @@ jvmOnly @@ nonFlaky,
      test("concurrent effects execute in parallel") {
        // Verify that effects are actually running in parallel
        for {
          ref   <- Ref.make(0)
          start <- Promise.make[Nothing, Unit]
          // This test ensures that multiple fibers execute concurrently
          _ <- ZStream
                 .range(0, 32)
                 .mapZIOPar(32) { _ =>
                   ref.update(_ + 1) *> start.await
                 }
                 .take(1)
                 .runDrain
                 .fork
          // Give fibers time to start and increment the ref
          _ <- Live.live(ZIO.sleep(100.millis))
          // Check that multiple fibers started (proving parallelism)
          count <- ref.get
          _     <- start.succeed(())
        } yield assertTrue(count > 1)
      } @@ jvmOnly @@ flaky
    )
}
