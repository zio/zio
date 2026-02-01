package zio.examples

import zio._
import zio.stream._
import zio.concurrent.CountdownLatch

/**
 * Demo program to demonstrate that parallelism > 16 now works correctly
 * with mapZIOPar after the fix.
 */
object MapZIOParDemo extends ZIOAppDefault {

  def run: ZIO[Any, Any, Unit] = {
    val parallelism = 32 // Greater than default buffer size of 16

    for {
      _ <- Console.printLine(s"Testing mapZIOPar with parallelism=$parallelism")
      _ <- Console.printLine("Before the fix, only 16 would execute in parallel")
      _ <- Console.printLine("After the fix, all 32 should execute in parallel\n")

      latch <- CountdownLatch.make(parallelism + 1)
      startTime <- Clock.nanoTime

      fiber <- ZStream
                .range(0, 100)
                .mapZIOPar(parallelism) { i =>
                  latch.countDown *> latch.await *> ZIO.succeed(i)
                }
                .runDrain
                .fork

      // Wait a bit for fibers to start
      _ <- ZIO.sleep(100.millis)

      // Check how many fibers are waiting
      countBefore <- latch.count
      _ <- Console.printLine(s"Fibers waiting: ${parallelism + 1 - countBefore}")
      _ <- Console.printLine(s"Expected: $parallelism")
      _ <- Console.printLine(s"Match: ${parallelism + 1 - countBefore == parallelism}")

      // Release all waiting fibers
      _ <- latch.countDown

      // Wait for completion
      _ <- fiber.join

      endTime <- Clock.nanoTime
      _ <- Console.printLine(s"\nCompleted in ${(endTime - startTime) / 1000000}ms")

      _ <- Console.printLine("\nTest with ordering preservation:")
      result <- ZStream
                  .range(0, 50)
                  .mapZIOPar(parallelism)(i => ZIO.succeed(i * 2))
                  .runCollect

      expected = Chunk.fromIterable((0 until 50).map(_ * 2))
      _ <- Console.printLine(s"Ordering preserved: ${result == expected}")

    } yield ()
  }
}
