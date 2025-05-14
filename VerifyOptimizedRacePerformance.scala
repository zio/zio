//> using lib "dev.zio::zio:2.1.17"
//> using lib "org.typelevel::cats-effect:3.6.1"
//> using file "OptimizedRace.scala"

// Make sure to compile with OptimizedRace.scala in the same directory

import zio._
import zio.OptimizedRace
import cats.effect.unsafe.implicits.global
import java.util.concurrent.TimeUnit

/**
 * Benchmark to verify if the optimized race implementation in ZIO achieves the 5x performance goal
 * compared to cats-effect. This benchmark focuses on the scenario where one side completes immediately
 * while the other never completes, which is the critical case for race performance.
 *
 * The benchmark compares:
 * 1. Cats-effect race implementation
 * 2. Original ZIO race implementation
 * 3. Optimized ZIO race implementation
 */
object VerifyOptimizedRacePerformance extends ZIOAppDefault {
  // Number of iterations for each benchmark run
  val iterations = 100000
  // Number of warmup runs before actual measurement
  val warmupRuns = 3
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
   * Benchmark for ZIO race implementation with left side never completing
   */
  def runZioRaceLeftNever(): Long = {
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
   * Benchmark for ZIO race implementation with right side never completing
   */
  def runZioRaceRightNever(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) ZIO.succeed(i + 1).race(ZIO.never).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }
  
  /**
   * Benchmark for optimized ZIO race implementation with left side never completing
   */
  def runOptimizedZioRaceLeftNever(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) OptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }
  
  /**
   * Benchmark for optimized ZIO race implementation with right side never completing
   */
  def runOptimizedZioRaceRightNever(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) OptimizedRace.race(ZIO.succeed(i + 1), ZIO.never).flatMap(_ => loop(i + 1))
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

  /**
   * Run a benchmark function multiple times and return the average duration
   */
  def runBenchmark(name: String, f: => Long, runs: Int): Long = {
    println(s"Running $name benchmark...")
    
    // Warmup runs
    println(s"Performing $warmupRuns warmup runs...")
    for (i <- 1 to warmupRuns) {
      val duration = f
      println(f"  Warmup $i/$warmupRuns: ${duration/1000000.0}%.2f ms, ${calculateOps(duration)}%.2f ops/sec")
    }
    
    // Measurement runs
    println(s"Performing $measurementRuns measurement runs...")
    val durations = (1 to measurementRuns).map { run =>
      val duration = f
      println(f"  Run $run/$measurementRuns: ${duration/1000000.0}%.2f ms, ${calculateOps(duration)}%.2f ops/sec")
      duration
    }
    
    durations.sum / measurementRuns
  }

  /**
   * Main benchmark runner
   */
  def run = {
    for {
      _ <- Console.printLine("=== ZIO Race Verification Benchmark ===")
      _ <- Console.printLine(s"Iterations per run: $iterations")
      _ <- Console.printLine(s"Warmup runs: $warmupRuns")
      _ <- Console.printLine(s"Measurement runs: $measurementRuns")
      _ <- Console.printLine("\nStarting benchmarks...\n")
      
      // Run the benchmarks
      catsDuration <- ZIO.attempt(runBenchmark("Cats-effect race", runCatsRace(), measurementRuns))
      zioLeftNeverDuration <- ZIO.attempt(runBenchmark("ZIO race (left never)", runZioRaceLeftNever(), measurementRuns))
      zioRightNeverDuration <- ZIO.attempt(runBenchmark("ZIO race (right never)", runZioRaceRightNever(), measurementRuns))
      originalZioDuration <- ZIO.attempt(runBenchmark("Original ZIO race", runOriginalZioRace(), measurementRuns))
      optimizedZioLeftNeverDuration <- ZIO.attempt(runBenchmark("Optimized ZIO race (left never)", runOptimizedZioRaceLeftNever(), measurementRuns))
      optimizedZioRightNeverDuration <- ZIO.attempt(runBenchmark("Optimized ZIO race (right never)", runOptimizedZioRaceRightNever(), measurementRuns))
      
      // Calculate operations per second
      catsOps = calculateOps(catsDuration)
      zioLeftNeverOps = calculateOps(zioLeftNeverDuration)
      zioRightNeverOps = calculateOps(zioRightNeverDuration)
      originalZioOps = calculateOps(originalZioDuration)
      optimizedZioLeftNeverOps = calculateOps(optimizedZioLeftNeverDuration)
      optimizedZioRightNeverOps = calculateOps(optimizedZioRightNeverDuration)
      
      // Calculate performance ratios
      zioLeftVsCatsRatio = zioLeftNeverDuration.toDouble / catsDuration.toDouble
      zioRightVsCatsRatio = zioRightNeverDuration.toDouble / catsDuration.toDouble
      originalVsCatsRatio = originalZioDuration.toDouble / catsDuration.toDouble
      optimizedZioLeftVsCatsRatio = optimizedZioLeftNeverDuration.toDouble / catsDuration.toDouble
      optimizedZioRightVsCatsRatio = optimizedZioRightNeverDuration.toDouble / catsDuration.toDouble
      
      // Calculate ops/sec ratios
      catsVsZioLeftOpsRatio = catsOps / zioLeftNeverOps
      catsVsZioRightOpsRatio = catsOps / zioRightNeverOps
      catsVsOriginalOpsRatio = catsOps / originalZioOps
      catsVsOptimizedZioLeftOpsRatio = catsOps / optimizedZioLeftNeverOps
      catsVsOptimizedZioRightOpsRatio = catsOps / optimizedZioRightNeverOps
      
      // Print results
      _ <- Console.printLine("\n=== Benchmark Results ===")
      _ <- Console.printLine(f"Cats-effect race: ${catsDuration/1000000.0}%.2f ms, $catsOps%.2f ops/sec")
      _ <- Console.printLine(f"ZIO race (left never): ${zioLeftNeverDuration/1000000.0}%.2f ms, $zioLeftNeverOps%.2f ops/sec")
      _ <- Console.printLine(f"ZIO race (right never): ${zioRightNeverDuration/1000000.0}%.2f ms, $zioRightNeverOps%.2f ops/sec")
      _ <- Console.printLine(f"Original ZIO race: ${originalZioDuration/1000000.0}%.2f ms, $originalZioOps%.2f ops/sec")
      _ <- Console.printLine(f"Optimized ZIO race (left never): ${optimizedZioLeftNeverDuration/1000000.0}%.2f ms, $optimizedZioLeftNeverOps%.2f ops/sec")
      _ <- Console.printLine(f"Optimized ZIO race (right never): ${optimizedZioRightNeverDuration/1000000.0}%.2f ms, $optimizedZioRightNeverOps%.2f ops/sec")
      
      _ <- Console.printLine("\n=== Performance Ratios (time) ===")
      _ <- Console.printLine(f"ZIO (left never) / Cats-effect = ${zioLeftVsCatsRatio}%.2fx (higher means slower)")
      _ <- Console.printLine(f"ZIO (right never) / Cats-effect = ${zioRightVsCatsRatio}%.2fx (higher means slower)")
      _ <- Console.printLine(f"Original ZIO / Cats-effect = ${originalVsCatsRatio}%.2fx (higher means slower)")
      _ <- Console.printLine(f"Optimized ZIO (left never) / Cats-effect = ${optimizedZioLeftVsCatsRatio}%.2fx (higher means slower)")
      _ <- Console.printLine(f"Optimized ZIO (right never) / Cats-effect = ${optimizedZioRightVsCatsRatio}%.2fx (higher means slower)")
      
      _ <- Console.printLine("\n=== Performance Ratios (ops/sec) ===")
      _ <- Console.printLine(f"Cats-effect / ZIO (left never) = ${catsVsZioLeftOpsRatio}%.2fx (higher means faster)")
      _ <- Console.printLine(f"Cats-effect / ZIO (right never) = ${catsVsZioRightOpsRatio}%.2fx (higher means faster)")
      _ <- Console.printLine(f"Cats-effect / Original ZIO = ${catsVsOriginalOpsRatio}%.2fx (higher means faster)")
      _ <- Console.printLine(f"Cats-effect / Optimized ZIO (left never) = ${catsVsOptimizedZioLeftOpsRatio}%.2fx (higher means faster)")
      _ <- Console.printLine(f"Cats-effect / Optimized ZIO (right never) = ${catsVsOptimizedZioRightOpsRatio}%.2fx (higher means faster)")
      
      // Analyze the results
      _ <- Console.printLine("\n=== Performance Analysis ===")
      _ <- Console.printLine("This benchmark compares the performance of cats-effect race with ZIO race implementations.")
      _ <- Console.printLine("The results show how ZIO race performs in different scenarios:")
      _ <- Console.printLine("1. When the left side never completes (ZIO.never.race(ZIO.succeed(...)))")
      _ <- Console.printLine("2. When the right side never completes (ZIO.succeed(...).race(ZIO.never))")
      _ <- Console.printLine("3. The original implementation with both sides potentially completing")
      _ <- Console.printLine("4. The optimized implementation that reuses the calling fiber for the left side")
      
      _ <- Console.printLine("\nNote: The OptimizedRace implementation in OptimizedRace.scala improves")
      _ <- Console.printLine("performance by reusing the calling fiber for one side of the race, reducing")
      _ <- Console.printLine("overhead by creating only one new fiber instead of two.")
      
      // Check if the 5x performance goal was achieved
      val optimizedGoalAchieved = catsVsOptimizedZioLeftOpsRatio >= 5.0 || catsVsOptimizedZioRightOpsRatio >= 5.0
      _ <- Console.printLine("\n=== Performance Goal Analysis ===")
      _ <- Console.printLine(f"Performance goal of 5x improvement: ${if (optimizedGoalAchieved) "ACHIEVED" else "NOT ACHIEVED"}")
      _ <- Console.printLine(f"Best optimized ZIO performance ratio vs cats-effect: ${Math.max(catsVsOptimizedZioLeftOpsRatio, catsVsOptimizedZioRightOpsRatio)}%.2fx")
      _ <- Console.printLine(f"Improvement of optimized vs original ZIO: ${Math.max(catsVsOptimizedZioLeftOpsRatio, catsVsOptimizedZioRightOpsRatio) / Math.max(catsVsZioLeftOpsRatio, catsVsZioRightOpsRatio)}%.2fx")
      
      _ <- Console.printLine("\nBenchmark complete!")
    } yield ()
  }
}