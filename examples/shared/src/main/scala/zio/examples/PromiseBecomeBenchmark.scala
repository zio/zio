package zio.examples

import zio._

/**
 * Benchmark example demonstrating the performance benefits of Promise.become()
 * compared to traditional Promise.await() patterns.
 * 
 * This addresses issue #9877: "Can Fiber(Runtime) and Promise be merged?"
 * The optimization reduces allocations and indirection when linking fibers to promises.
 */
object PromiseBecomeBenchmark extends ZIOAppDefault {

  def run = for {
    _ <- Console.printLine("Promise.become() Performance Benchmark")
    _ <- Console.printLine("=====================================")
    
    // Benchmark traditional approach vs become() approach
    _ <- traditionalApproachBenchmark
    _ <- becomeApproachBenchmark
    
  } yield ()

  /**
   * Traditional approach: Fork a fiber, then await a promise that gets completed
   * by the fiber result. This creates unnecessary allocations and indirection.
   */
  val traditionalApproachBenchmark = for {
    _ <- Console.printLine("\n1. Traditional Approach (Fork -> Complete Promise)")
    start <- Clock.nanoTime
    _ <- ZIO.foreachDiscard(1 to 10000) { i =>
      for {
        promise <- Promise.make[String, Int]
        fiber   <- (ZIO.sleep(1.nano) *> ZIO.succeed(i)).fork
        result  <- fiber.await
        _       <- promise.succeed(result)
        value   <- promise.await
      } yield value
    }
    end <- Clock.nanoTime
    duration = (end - start) / 1_000_000 // Convert to milliseconds
    _ <- Console.printLine(s"Traditional approach took: ${duration}ms")
  } yield ()

  /**
   * Optimized approach using Promise.become(): Link the promise directly to
   * the fiber, avoiding intermediate allocations and callback indirection.
   */
  val becomeApproachBenchmark = for {
    _ <- Console.printLine("\n2. Optimized Approach (Promise.become())")
    start <- Clock.nanoTime
    _ <- ZIO.foreachDiscard(1 to 10000) { i =>
      for {
        promise <- Promise.make[String, Int]
        fiber   <- (ZIO.sleep(1.nano) *> ZIO.succeed(i)).fork
        _       <- promise.become(fiber)
        value   <- promise.await
      } yield value
    }
    end <- Clock.nanoTime
    duration = (end - start) / 1_000_000 // Convert to milliseconds
    _ <- Console.printLine(s"Optimized approach took: ${duration}ms")
  } yield ()
}