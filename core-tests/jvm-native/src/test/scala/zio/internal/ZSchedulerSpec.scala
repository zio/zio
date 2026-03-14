package zio.internal

import zio._
import zio.test._
import zio.test.Assertion._

object ZSchedulerSpec extends ZIOBaseSpec {

  def spec =
    suite("ZSchedulerSpec")(
      test("scheduler completes high-throughput fork/join without deadlock") {
        // Validates that the optimized maybeUnparkWorker still wakes workers
        // when needed under high concurrency. A regression in the unpark logic
        // would cause this to deadlock or timeout.
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(10000)
          effect = ref.modify(n =>
                     (if (n == 1) promise.succeed(()) else ZIO.unit, n - 1)
                   ).flatten
          _ <- ZIO.foreachParDiscard((1 to 10000).toList)(_ => effect.forkDaemon)
          _ <- promise.await
        } yield assertCompletes
      } @@ TestAspect.timeout(10.seconds) @@ TestAspect.nonFlaky,
      test("scheduler handles chained forks correctly") {
        // Chained forks stress the cascade-notification path in maybeUnparkWorker.
        // Each fork submits a new task; if the searching > 0 early-return is too
        // aggressive, the chain would stall.
        def iterate(promise: Promise[Nothing, Unit], n: Int): UIO[Any] =
          if (n <= 0) promise.succeed(())
          else ZIO.unit.flatMap(_ => iterate(promise, n - 1).forkDaemon)

        for {
          promise <- Promise.make[Nothing, Unit]
          _       <- iterate(promise, 1000).forkDaemon
          _       <- promise.await
        } yield assertCompletes
      } @@ TestAspect.timeout(10.seconds) @@ TestAspect.nonFlaky,
      test("scheduler handles ping-pong communication between fibers") {
        // Tests that workers are properly unparked when fibers communicate
        // through queues, a pattern sensitive to park/unpark timing.
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(500)
          queue   <- Queue.bounded[Unit](1)
          effect = queue.offer(()).forkDaemon *>
                     queue.take *>
                     ref.modify(n => (if (n == 1) promise.succeed(()) else ZIO.unit, n - 1)).flatten
          _ <- ZIO.foreachParDiscard((1 to 500).toList)(_ => effect.forkDaemon)
          _ <- promise.await
        } yield assertCompletes
      } @@ TestAspect.timeout(10.seconds) @@ TestAspect.nonFlaky,
      test("scheduler handles yield-heavy workloads") {
        // Exercises submitAndYield path which also calls maybeUnparkWorker.
        // Ensures workers remain responsive when fibers yield frequently.
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(100)
          effect = ZIO.foreachDiscard((1 to 500).toList)(_ => ZIO.yieldNow) *>
                     ref.modify(n => (if (n == 1) promise.succeed(()) else ZIO.unit, n - 1)).flatten
          _ <- ZIO.foreachParDiscard((1 to 100).toList)(_ => effect.forkDaemon)
          _ <- promise.await
        } yield assertCompletes
      } @@ TestAspect.timeout(10.seconds) @@ TestAspect.nonFlaky,
      test("scheduler correctly reports execution metrics") {
        for {
          executor <- ZIO.executor
          metrics  <- ZIO.succeed(executor.metrics(Unsafe.unsafe))
        } yield assert(metrics)(isSome) &&
          assert(metrics.get.concurrency)(isGreaterThan(0))
      }
    )
}
