//> using lib "dev.zio::zio:2.1.17"
//> using lib "org.typelevel::cats-effect:3.6.1"
//> using file "/Users/diouf/zio/OptimizedRace.scala"

import zio._
import cats.effect.unsafe.implicits.global

/**
 * Comprehensive benchmark to verify if the optimized race implementation in ZIO achieves the 5x performance goal
 * compared to cats-effect, and to test various race scenarios.
 */
object OptimizedRaceBenchmark extends ZIOAppDefault {
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
      if (i < iterations) IO.race(IO.never, IO.pure(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)
    
    loop(0).unsafeRunSync()
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }

  /**
   * Benchmark for standard ZIO race implementation
   */
  def runStandardZioRace(): Long = {
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
   * Benchmark for standard ZIO race with both sides completing
   */
  def runStandardZioRaceBothComplete(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) ZIO.succeed(i).race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }

  /**
   * Benchmark for optimized ZIO race with both sides completing
   */
  def runOptimizedZioRaceBothComplete(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) OptimizedRace.race(ZIO.succeed(i), ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = java.lang.System.nanoTime()
    endTime - startTime
  }

  /**
   * Benchmark for standard ZIO race with left side completing
   */
  def runStandardZioRaceLeftComplete(): Long = {
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
   * Benchmark for optimized ZIO race with left side completing
   */
  def runOptimizedZioRaceLeftComplete(): Long = {
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
   * Run a benchmark multiple times and return the average duration
   */
  def runBenchmark(name: String, benchmark: () => Long): Long = {
    // Warmup runs
    for (i <- 1 to warmupRuns) {
      benchmark()
      Console.printLine(s"Warmup $i for $name complete").orDie
    }
    
    // Measurement runs
    val durations = for (i <- 1 to measurementRuns) yield {
      val duration = benchmark()
      Console.printLine(s"Measurement $i for $name: ${duration/1000000.0} ms").orDie
      duration
    }
    
    // Calculate average
    durations.sum / measurementRuns
  }

  def run = {
    for {
      _ <- Console.printLine("=== ZIO Optimized Race Benchmark ===\n")
      _ <- Console.printLine(s"Iterations: $iterations")
      _ <- Console.printLine(s"Warmup runs: $warmupRuns")
      _ <- Console.printLine(s"Measurement runs: $measurementRuns\n")
      
      // Run cats-effect benchmark
      _ <- Console.printLine("Running cats-effect race benchmark...")
      catsDuration <- ZIO.attempt(runBenchmark("cats-effect race", runCatsRace))
      catsOps = calculateOps(catsDuration)
      _ <- Console.printLine(f"Cats-effect race: ${catsDuration/1000000.0}%.2f ms, $catsOps%.2f ops/sec\n")
      
      // Run standard ZIO benchmark
      _ <- Console.printLine("Running standard ZIO race benchmark...")
      standardZioDuration <- ZIO.attempt(runBenchmark("standard ZIO race", runStandardZioRace))
      standardZioOps = calculateOps(standardZioDuration)
      _ <- Console.printLine(f"Standard ZIO race: ${standardZioDuration/1000000.0}%.2f ms, $standardZioOps%.2f ops/sec\n")
      
      // Run optimized ZIO benchmark
      _ <- Console.printLine("Running optimized ZIO race benchmark...")
      optimizedZioDuration <- ZIO.attempt(runBenchmark("optimized ZIO race", runOptimizedZioRace))
      optimizedZioOps = calculateOps(optimizedZioDuration)
      _ <- Console.printLine(f"Optimized ZIO race: ${optimizedZioDuration/1000000.0}%.2f ms, $optimizedZioOps%.2f ops/sec\n")
      
      // Run standard ZIO benchmark with both sides completing
      _ <- Console.printLine("Running standard ZIO race benchmark (both sides complete)...")
      standardBothDuration <- ZIO.attempt(runBenchmark("standard ZIO race (both complete)", runStandardZioRaceBothComplete))
      standardBothOps = calculateOps(standardBothDuration)
      _ <- Console.printLine(f"Standard ZIO race (both complete): ${standardBothDuration/1000000.0}%.2f ms, $standardBothOps%.2f ops/sec\n")
      
      // Run optimized ZIO benchmark with both sides completing
      _ <- Console.printLine("Running optimized ZIO race benchmark (both sides complete)...")
      optimizedBothDuration <- ZIO.attempt(runBenchmark("optimized ZIO race (both complete)", runOptimizedZioRaceBothComplete))
      optimizedBothOps = calculateOps(optimizedBothDuration)
      _ <- Console.printLine(f"Optimized ZIO race (both complete): ${optimizedBothDuration/1000000.0}%.2f ms, $optimizedBothOps%.2f ops/sec\n")
      
      // Run standard ZIO benchmark with left side completing
      _ <- Console.printLine("Running standard ZIO race benchmark (left side completes)...")
      standardLeftDuration <- ZIO.attempt(runBenchmark("standard ZIO race (left completes)", runStandardZioRaceLeftComplete))
      standardLeftOps = calculateOps(standardLeftDuration)
      _ <- Console.printLine(f"Standard ZIO race (left completes): ${standardLeftDuration/1000000.0}%.2f ms, $standardLeftOps%.2f ops/sec\n")
      
      // Run optimized ZIO benchmark with left side completing
      _ <- Console.printLine("Running optimized ZIO race benchmark (left side completes)...")
      optimizedLeftDuration <- ZIO.attempt(runBenchmark("optimized ZIO race (left completes)", runOptimizedZioRaceLeftComplete))
      optimizedLeftOps = calculateOps(optimizedLeftDuration)
      _ <- Console.printLine(f"Optimized ZIO race (left completes): ${optimizedLeftDuration/1000000.0}%.2f ms, $optimizedLeftOps%.2f ops/sec\n")
      
      // Calculate performance ratios
      zioVsCatsRatio = standardZioOps / catsOps
      optimizedVsCatsRatio = optimizedZioOps / catsOps
      optimizedVsStandardRatio = optimizedZioOps / standardZioOps
      optimizedBothVsStandardBothRatio = optimizedBothOps / standardBothOps
      optimizedLeftVsStandardLeftRatio = optimizedLeftOps / standardLeftOps
      
      _ <- Console.printLine("=== Performance Analysis ===\n")
      _ <- Console.printLine(f"Standard ZIO / Cats-effect = ${zioVsCatsRatio}%.2fx")
      _ <- Console.printLine(f"Optimized ZIO / Cats-effect = ${optimizedVsCatsRatio}%.2fx")
      _ <- Console.printLine(f"Optimized ZIO / Standard ZIO = ${optimizedVsStandardRatio}%.2fx")
      _ <- Console.printLine(f"Optimized ZIO (both complete) / Standard ZIO (both complete) = ${optimizedBothVsStandardBothRatio}%.2fx")
      _ <- Console.printLine(f"Optimized ZIO (left completes) / Standard ZIO (left completes) = ${optimizedLeftVsStandardLeftRatio}%.2fx")
      
      // Check if the 5x performance goal was achieved
      goalAchieved = optimizedVsCatsRatio >= 5.0
      _ <- Console.printLine(f"\nPerformance goal of 5x improvement over cats-effect: ${if (goalAchieved) "ACHIEVED" else "NOT ACHIEVED"}")
      
      // Check if optimized implementation is faster than standard ZIO
      optimizationSuccessful = optimizedVsStandardRatio > 1.0
      _ <- Console.printLine(f"Optimization successful (faster than standard ZIO): ${if (optimizationSuccessful) "YES" else "NO"}")
      
      _ <- Console.printLine("\nBenchmark complete!")
    } yield ()
  }
}