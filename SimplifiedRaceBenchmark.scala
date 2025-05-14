//> using lib "dev.zio::zio:2.1.17"
//> using lib "org.typelevel::cats-effect:3.6.1"

import zio._
import cats.effect.unsafe.implicits.global
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simplified benchmark to verify if the optimized race implementation in ZIO achieves the 5x performance goal
 * compared to cats-effect. This benchmark focuses on the scenario where one side completes immediately
 * while the other never completes, which is the critical case for race performance.
 */
object SimplifiedRaceBenchmark extends ZIOAppDefault {
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
   * Simplified implementation of optimized race that reuses the calling fiber
   * for the left side of the race.
   */
  def optimizedRace[R, E, A, B](left: ZIO[R, E, A], right: ZIO[R, E, B]): ZIO[R, E, Either[A, B]] = {
    ZIO.suspendSucceed {
      // Create a promise to hold the result
      val promise = Promise.make[E, Either[A, B]]
      
      // Fork the right side in a separate fiber
      val rightFork = right.map(Right(_)).fork
      
      for {
        // Get the right fiber
        rightFiber <- rightFork
        // Set up a race between left completing and right completing
        result <- left.map(Left(_)).foldCauseZIO(
          cause => promise.fail(cause) *> ZIO.failCause(cause),
          value => promise.succeed(value) *> ZIO.succeed(value)
        ).race(rightFiber.join)
        // Interrupt the right fiber if left won
        _ <- ZIO.when(result.isLeft)(rightFiber.interrupt)
      } yield result
    }
  }
  
  /**
   * Benchmark for our simplified optimized race implementation
   */
  def runOptimizedRace(): Long = {
    val startTime = java.lang.System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) {
        optimizedRace(ZIO.never, ZIO.succeed(i + 1)).flatMap {
          case Right(value) => loop(value)
          case Left(_) => ZIO.succeed(i) // This should never happen
        }
      } else ZIO.succeed(i)
    
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
      _ <- Console.printLine("=== ZIO Race Verification Benchmark ===\n")
      _ <- Console.printLine(s"Iterations per run: $iterations")
      _ <- Console.printLine(s"Warmup runs: $warmupRuns")
      _ <- Console.printLine(s"Measurement runs: $measurementRuns")
      _ <- Console.printLine("\nStarting benchmarks...\n")
      
      // Run the benchmarks
      catsDuration <- ZIO.attempt(runBenchmark("Cats-effect race", runCatsRace(), measurementRuns))
      standardZioDuration <- ZIO.attempt(runBenchmark("Standard ZIO race", runStandardZioRace(), measurementRuns))
      optimizedZioDuration <- ZIO.attempt(runBenchmark("Optimized ZIO race", runOptimizedRace(), measurementRuns))
      
      // Calculate operations per second
      catsOps = calculateOps(catsDuration)
      standardZioOps = calculateOps(standardZioDuration)
      optimizedZioOps = calculateOps(optimizedZioDuration)
      
      // Calculate performance ratios
      standardZioVsCatsRatio = standardZioDuration.toDouble / catsDuration.toDouble
      optimizedZioVsCatsRatio = optimizedZioDuration.toDouble / catsDuration.toDouble
      
      // Calculate ops/sec ratios
      catsVsStandardZioOpsRatio = standardZioOps / catsOps
      catsVsOptimizedZioOpsRatio = optimizedZioOps / catsOps
      optimizedVsStandardRatio = optimizedZioOps / standardZioOps
      
      // Print results
      _ <- Console.printLine("\n=== Benchmark Results ===\n")
      _ <- Console.printLine(f"Cats-effect race: ${catsDuration/1000000.0}%.2f ms, $catsOps%.2f ops/sec")
      _ <- Console.printLine(f"Standard ZIO race: ${standardZioDuration/1000000.0}%.2f ms, $standardZioOps%.2f ops/sec")
      _ <- Console.printLine(f"Optimized ZIO race: ${optimizedZioDuration/1000000.0}%.2f ms, $optimizedZioOps%.2f ops/sec")
      
      _ <- Console.printLine("\n=== Performance Ratios ===\n")
      _ <- Console.printLine(f"Standard ZIO / Cats-effect = ${standardZioVsCatsRatio}%.2fx (lower means faster)")
      _ <- Console.printLine(f"Optimized ZIO / Cats-effect = ${optimizedZioVsCatsRatio}%.2fx (lower means faster)")
      _ <- Console.printLine(f"Optimized ZIO / Standard ZIO = ${optimizedZioDuration.toDouble / standardZioDuration.toDouble}%.2fx (lower means faster)")
      
      _ <- Console.printLine("\n=== Operations per Second Ratios ===\n")
      _ <- Console.printLine(f"Standard ZIO / Cats-effect = ${catsVsStandardZioOpsRatio}%.2fx (higher means faster)")
      _ <- Console.printLine(f"Optimized ZIO / Cats-effect = ${catsVsOptimizedZioOpsRatio}%.2fx (higher means faster)")
      _ <- Console.printLine(f"Optimized ZIO / Standard ZIO = ${optimizedVsStandardRatio}%.2fx (higher means faster)")
      
      // Check if the 5x performance goal was achieved
      val optimizedGoalAchieved = catsVsOptimizedZioOpsRatio >= 5.0
      _ <- Console.printLine("\n=== Performance Goal Analysis ===\n")
      _ <- Console.printLine(f"Performance goal of 5x improvement over cats-effect: ${if (optimizedGoalAchieved) "ACHIEVED" else "NOT ACHIEVED"}")
      _ <- Console.printLine(f"Actual improvement over cats-effect: ${catsVsOptimizedZioOpsRatio}%.2fx")
      _ <- Console.printLine(f"Improvement over standard ZIO implementation: ${optimizedVsStandardRatio}%.2fx")
      
      _ <- Console.printLine("\nBenchmark complete!")
    } yield ()
  }
}