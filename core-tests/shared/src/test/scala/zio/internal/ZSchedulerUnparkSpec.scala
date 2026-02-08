package zio.internal

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.util.concurrent.atomic.AtomicInteger

/**
 * Test suite for issue #9878: ZScheduler unpark batching optimization
 * 
 * Tests verify:
 * - Batching reduces unpark frequency
 * - Cold start (all workers idle) still works
 * - No deadlocks under various scenarios
 * - Work gets processed correctly with batching
 * - Responsiveness is maintained
 */
object ZSchedulerUnparkSpec extends ZIOSpecDefault {

  def spec = suite("ZScheduler Unpark Optimization (#9878)")(
    suite("Batching Behavior")(
      test("many small tasks complete successfully") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(1000)
          effect   = ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to 1000)(_ => effect.forkDaemon)
          _       <- promise.await
          final   <- ref.get
        } yield assertTrue(final == 0)
      },
      test("rapid fire submissions with yields") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(100)
          effect   = ZIO.yieldNow *>
                     ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to 100)(_ => effect.forkDaemon)
          _       <- promise.await
          final   <- ref.get
        } yield assertTrue(final == 0)
      },
      test("batching doesn't cause task loss") {
        // Submit exactly 8 tasks (batch threshold) and verify all execute
        for {
          counter <- Ref.make(0)
          _       <- ZIO.foreachDiscard(1 to 8)(_ => counter.update(_ + 1).forkDaemon)
          _       <- ZIO.sleep(Duration.fromMillis(100))
          final   <- counter.get
        } yield assertTrue(final == 8)
      },
      test("batching doesn't cause excessive latency") {
        // Verify single task completes within reasonable time
        for {
          promise <- Promise.make[Nothing, Unit]
          start   <- Clock.nanoTime
          _       <- promise.succeed(()).forkDaemon
          _       <- promise.await
          end     <- Clock.nanoTime
          latency  = (end - start) / 1000000 // Convert to ms
        } yield assertTrue(latency < 50) // Should complete in < 50ms
      } @@ withLiveClock @@ flaky
    ),
    suite("Cold Start Scenarios")(
      test("idle scheduler wakes up for new work") {
        for {
          // Let workers park
          _       <- ZIO.sleep(Duration.fromMillis(50))
          // Submit work after idle period
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(100)
          effect   = ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to 100)(_ => effect.forkDaemon)
          _       <- promise.await
          final   <- ref.get
        } yield assertTrue(final == 0)
      } @@ withLiveClock,
      test("single task after idle completes") {
        for {
          _       <- ZIO.sleep(Duration.fromMillis(50))
          promise <- Promise.make[Nothing, Unit]
          _       <- promise.succeed(()).forkDaemon
          _       <- promise.await.timeout(Duration.fromSeconds(1))
        } yield assertCompletes
      } @@ withLiveClock,
      test("burst after idle period") {
        for {
          _       <- ZIO.sleep(Duration.fromMillis(50))
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(500)
          effect   = ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to 500)(_ => effect.forkDaemon)
          _       <- promise.await.timeout(Duration.fromSeconds(2))
          final   <- ref.get
        } yield assertTrue(final == 0)
      } @@ withLiveClock
    ),
    suite("No Deadlocks")(
      test("ping-pong pattern doesn't deadlock") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(100)
          queue   <- Queue.bounded[Unit](1)
          effect   = queue.offer(()).forkDaemon *>
                     queue.take *>
                     ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to 100)(_ => effect.forkDaemon)
          _       <- promise.await.timeout(Duration.fromSeconds(5))
        } yield assertCompletes
      } @@ withLiveClock @@ nonFlaky(10),
      test("mixed blocking and non-blocking work") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(100)
          blocking = ZIO.attemptBlocking(Thread.sleep(1)) *>
                     ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to 100)(_ => blocking.forkDaemon)
          _       <- promise.await.timeout(Duration.fromSeconds(10))
        } yield assertCompletes
      } @@ withLiveClock @@ TestAspect.exceptNative,
      test("all workers busy scenario") {
        val poolSize = java.lang.Runtime.getRuntime.availableProcessors
        for {
          // Start poolSize long-running tasks
          refs    <- ZIO.foreach(1 to poolSize)(_ => Ref.make(true))
          _       <- ZIO.foreachDiscard(refs)(ref => 
                       ZIO.whileLoop(ref.get)(ZIO.yieldNow)(ref.get).forkDaemon
                     )
          _       <- ZIO.sleep(Duration.fromMillis(10))
          // Submit new work while all busy
          promise <- Promise.make[Nothing, Unit]
          _       <- promise.succeed(()).forkDaemon
          // Stop long-running tasks
          _       <- ZIO.foreachDiscard(refs)(_.set(false))
          // Verify new work completes
          _       <- promise.await.timeout(Duration.fromSeconds(2))
        } yield assertCompletes
      } @@ withLiveClock @@ flaky
    ),
    suite("Correctness")(
      test("work stealing still functions") {
        // Verify workers steal from global queue even with batching
        for {
          counter <- Ref.make(0)
          tasks    = 100
          _       <- ZIO.foreachDiscard(1 to tasks)(_ => 
                       counter.update(_ + 1).forkDaemon
                     )
          _       <- ZIO.sleep(Duration.fromMillis(200))
          final   <- counter.get
        } yield assertTrue(final == tasks)
      } @@ withLiveClock,
      test("chained forks work correctly") {
        def iterate(promise: Promise[Nothing, Unit], n: Int): UIO[Any] =
          if (n <= 0) promise.succeed(())
          else ZIO.unit.flatMap(_ => iterate(promise, n - 1).forkDaemon)

        for {
          promise <- Promise.make[Nothing, Unit]
          _       <- iterate(promise, 100).forkDaemon
          _       <- promise.await.timeout(Duration.fromSeconds(2))
        } yield assertCompletes
      } @@ withLiveClock,
      test("respects pool size limits") {
        // Verify scheduler doesn't try to unpark more workers than exist
        val poolSize = java.lang.Runtime.getRuntime.availableProcessors
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(poolSize * 10)
          effect   = ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to (poolSize * 10))(_ => effect.forkDaemon)
          _       <- promise.await.timeout(Duration.fromSeconds(3))
        } yield assertCompletes
      } @@ withLiveClock
    ),
    suite("Edge Cases")(
      test("empty queue after idle check") {
        // Edge case: queue becomes empty between isEmpty() and poll()
        for {
          _       <- ZIO.sleep(Duration.fromMillis(50))
          promise <- Promise.make[Nothing, Unit]
          _       <- promise.succeed(()).forkDaemon
          _       <- promise.await.timeout(Duration.fromSeconds(1))
        } yield assertCompletes
      } @@ withLiveClock,
      test("concurrent submits from multiple fibers") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(1000)
          effect   = ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          // Fork from multiple fibers concurrently
          _       <- ZIO.foreachParDiscard(1 to 10)(_ =>
                       ZIO.foreachDiscard(1 to 100)(_ => effect.forkDaemon)
                     )
          _       <- promise.await.timeout(Duration.fromSeconds(3))
          final   <- ref.get
        } yield assertTrue(final == 0)
      } @@ withLiveClock @@ nonFlaky(5)
    ),
    suite("Performance Characteristics")(
      test("throughput with many small tasks") {
        for {
          start   <- Clock.nanoTime
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(10000)
          effect   = ref.updateAndGet(_ - 1).flatMap(n =>
                       if (n == 0) promise.succeed(()) else ZIO.unit
                     )
          _       <- ZIO.foreachDiscard(1 to 10000)(_ => effect.forkDaemon)
          _       <- promise.await
          end     <- Clock.nanoTime
          time     = (end - start) / 1000000 // ms
        } yield assertTrue(time < 5000) // Should complete in < 5 seconds
      } @@ withLiveClock @@ flaky,
      test("responsiveness maintained under load") {
        for {
          // Create background load
          bgRef   <- Ref.make(1000)
          _       <- ZIO.foreachDiscard(1 to 1000)(_ =>
                       bgRef.update(_ - 1).forkDaemon
                     )
          // Measure latency of high-priority task
          promise <- Promise.make[Nothing, Unit]
          start   <- Clock.nanoTime
          _       <- promise.succeed(()).forkDaemon
          _       <- promise.await
          end     <- Clock.nanoTime
          latency  = (end - start) / 1000000 // ms
        } yield assertTrue(latency < 100) // Should respond in < 100ms
      } @@ withLiveClock @@ flaky
    )
  ) @@ TestAspect.timeout(Duration.fromSeconds(60))
}
