//> using lib "dev.zio::zio:2.1.17"
//> using lib "org.typelevel::cats-effect:3.6.1"
//> using file "OptimizedRace.scala"

import zio._
import zio.Unsafe
import cats.effect.unsafe.implicits.global
import java.util.concurrent.TimeUnit

/**
 * Benchmark to verify if the optimized race implementation in ZIO achieves the 5x performance goal
 * compared to cats-effect. This benchmark measures the throughput of race operations where one side
 * completes immediately and the other never completes.
 *
 * The benchmark compares:
 * 1. Original ZIO race implementation
 * 2. Optimized ZIO race implementation
 * 3. Cats-effect race implementation
 */
object UpdatedRaceOptimizationBenchmark extends ZIOAppDefault {
  // Number of iterations for each benchmark run
  val iterations = 100000
  // Number of warmup runs before actual measurement
  val warmupRuns = 5
  // Number of measurement runs to average
  val measurementRuns = 5
  
  /**
   * Benchmark for cats-effect race implementation
   */
  def runCatsRace(): Long = {
    import cats.effect.IO
    
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): IO[Int] =
      if (i < iterations) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)
    
    loop(0).unsafeRunSync()
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }

  /**
   * Benchmark for original ZIO race implementation
   */
  def runOriginalZioRace(): Long = {
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
   * Benchmark for optimized ZIO race implementation
   */
  def runOptimizedZioRace(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] = {
      implicit val trace = Trace.empty
      if (i < iterations) OptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    }
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }

  /**
   * Run a benchmark with warmup and multiple measurements
   */
  def runBenchmark(name: String, benchmark: () => Long): Double = {
    println(s"Running $name benchmark...")
    
    // Warmup runs
    println("Warming up...")
    for (i <- 1 to warmupRuns) {
      val time = benchmark()
      println(f"  Warmup run $i: ${time / 1_000_000.0}%.2f ms")
    }
    
    // Measurement runs
    println("Measuring...")
    val measurements = for (i <- 1 to measurementRuns) yield {
      val time = benchmark()
      println(f"  Measurement run $i: ${time / 1_000_000.0}%.2f ms")
      time
    }
    
    // Calculate average
    val avgTime = measurements.sum.toDouble / measurementRuns
    val avgTimeMs = avgTime / 1_000_000.0
    val opsPerSec = (iterations.toDouble / avgTime) * 1_000_000_000
    
    println(f"  Average time: $avgTimeMs%.2f ms")
    println(f"  Operations per second: $opsPerSec%.2f ops/s")
    println()
    
    avgTime
  }

  /**
   * Main benchmark program
   */
  def run = for {
    _ <- Console.printLine("=== ZIO Race Optimization Benchmark ===\n")
    _ <- Console.printLine(s"Iterations per run: $iterations")
    _ <- Console.printLine(s"Warmup runs: $warmupRuns")
    _ <- Console.printLine(s"Measurement runs: $measurementRuns\n")
    
    // Run benchmarks
    catsTime <- ZIO.attempt(runBenchmark("Cats-effect race", runCatsRace))
    originalZioTime <- ZIO.attempt(runBenchmark("Original ZIO race", runOriginalZioRace))
    optimizedZioTime <- ZIO.attempt(runBenchmark("Optimized ZIO race", runOptimizedZioRace))
    
    // Calculate performance ratios
    catsToOriginalRatio = catsTime / originalZioTime
    catsToOptimizedRatio = catsTime / optimizedZioTime
    originalToOptimizedRatio = originalZioTime / optimizedZioTime
    
    // Print results
    _ <- Console.printLine("=== Performance Ratios ===\n")
    _ <- Console.printLine(f"Cats-Effect / Original ZIO: $catsToOriginalRatio%.2f")
    _ <- Console.printLine(f"Cats-Effect / Optimized ZIO: $catsToOptimizedRatio%.2f")
    _ <- Console.printLine(f"Original ZIO / Optimized ZIO: $originalToOptimizedRatio%.2f\n")
    
    // Check if performance goal is met
    _ <- Console.printLine("=== Performance Goal Analysis ===\n")
    _ <- if (catsToOptimizedRatio >= 5.0)
           Console.printLine("✅ Performance goal achieved! The optimized ZIO race implementation is at least 5x faster than cats-effect.")
         else
           Console.printLine(f"❌ Performance goal not met. The optimized ZIO race implementation is only ${catsToOptimizedRatio}%.2fx faster than cats-effect.")
  } yield ()
}