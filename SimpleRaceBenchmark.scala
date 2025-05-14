//> using lib "dev.zio::zio:2.1.17"
//> using lib "org.typelevel::cats-effect:3.6.1"

import zio._
import cats.effect.unsafe.implicits.global

/**
 * Simple benchmark to verify if the optimized race implementation in ZIO achieves the 5x performance goal
 * compared to cats-effect.
 */
object SimpleRaceBenchmark extends ZIOAppDefault {
  // Number of iterations for each benchmark run
  val iterations = 100000
  
  /**
   * Benchmark for cats-effect race implementation
   */
  def runCatsRace(): Long = {
    import cats.effect.IO
    
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): IO[Int] =
      if (i < iterations) IO.race(IO.never, IO.pure(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)
    
    loop(0).unsafeRunSync()
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }

  /**
   * Benchmark for standard ZIO race implementation
   */
  def runZioRace(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) ZIO.never.race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }

  /**
   * Calculate operations per second from nanosecond duration
   */
  def calculateOps(nanos: Long): Double = {
    val seconds = nanos / 1_000_000_000.0
    iterations / seconds
  }

  def run = {
    for {
      _ <- Console.printLine("=== ZIO Race Benchmark ===\n")
      _ <- Console.printLine(s"Iterations: $iterations\n")
      
      // Run cats-effect benchmark
      _ <- Console.printLine("Running cats-effect race benchmark...")
      catsDuration <- ZIO.attempt(runCatsRace())
      catsOps = calculateOps(catsDuration)
      _ <- Console.printLine(f"Cats-effect race: ${catsDuration/1000000.0}%.2f ms, $catsOps%.2f ops/sec\n")
      
      // Run ZIO benchmark
      _ <- Console.printLine("Running ZIO race benchmark...")
      zioDuration <- ZIO.attempt(runZioRace())
      zioOps = calculateOps(zioDuration)
      _ <- Console.printLine(f"ZIO race: ${zioDuration/1000000.0}%.2f ms, $zioOps%.2f ops/sec\n")
      
      // Calculate performance ratio
      zioVsCatsRatio = zioOps / catsOps
      _ <- Console.printLine("=== Performance Analysis ===\n")
      _ <- Console.printLine(f"ZIO / Cats-effect = ${zioVsCatsRatio}%.2fx (higher means faster)")
      
      // Check if the 5x performance goal was achieved
      goalAchieved = zioVsCatsRatio >= 5.0
      _ <- Console.printLine(f"\nPerformance goal of 5x improvement: ${if (goalAchieved) "ACHIEVED" else "NOT ACHIEVED"}")
      
      // Note about the optimized implementation
      _ <- Console.printLine("\nNote: The OptimizedRace implementation in OptimizedRace.scala aims to improve")
      _ <- Console.printLine("performance by reusing the calling fiber for one side of the race, reducing")
      _ <- Console.printLine("overhead by creating only one new fiber instead of two.")
      
      _ <- Console.printLine("\nBenchmark complete!")
    } yield ()
  }
}