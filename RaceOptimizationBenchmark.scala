//> using lib "dev.zio::zio:2.0.15"
//> using lib "org.typelevel::cats-effect:3.5.1"

import zio._
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
object RaceOptimizationBenchmark {
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
    
    val startTime = System.nanoTime()
    
    def loop(i: Int): IO[Int] =
      if (i < iterations) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)
    
    loop(0).unsafeRunSync()
    
    val endTime = System.nanoTime()
    endTime - startTime
  }

  /**
   * Benchmark for original ZIO race implementation
   */
  def runOriginalZioRace(): Long = {
    val startTime = System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) ZIO.never.race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = System.nanoTime()
    endTime - startTime
  }

  /**
   * Benchmark for optimized ZIO race implementation
   */
  def runOptimizedZioRace(): Long = {
    val startTime = System.nanoTime()
    
    def loop(i: Int): UIO[Int] = {
      implicit val trace = Trace.empty
      if (i < iterations) OptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    }
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = System.nanoTime()
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
    val durations = (1 to runs).map { run =>
      val duration = f
      println(f"$name (Run $run/$runs): ${duration/1000000.0}%.2f ms, ${calculateOps(duration)}%.2f ops/sec")
      duration
    }
    durations.sum / runs
  }

  def main(args: Array[String]): Unit = {
    println("=== ZIO Race Optimization Benchmark ===\n")
    println(s"Benchmark configuration:")
    println(s"- Iterations per run: $iterations")
    println(s"- Warmup runs: $warmupRuns")
    println(s"- Measurement runs: $measurementRuns\n")
    
    // Run warmup
    println("Running warmup...")
    try {
      (1 to warmupRuns).foreach { _ =>
        runCatsRace()
        runOriginalZioRace()
        runOptimizedZioRace()
      }
    } catch {
      case e: Exception => 
        println(s"Warmup error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    println("\nRunning actual benchmark...")
    
    // Measure cats-effect race
    var catsTime: Long = 0
    try {
      catsTime = runBenchmark("Cats-Effect race", runCatsRace(), measurementRuns)
      println(f"\nCats-Effect race average: ${catsTime/1000000.0}%.2f ms, ${calculateOps(catsTime)}%.2f ops/sec")
    } catch {
      case e: Exception => 
        println(s"Cats-effect benchmark error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    // Measure original ZIO race
    var originalZioTime: Long = 0
    try {
      originalZioTime = runBenchmark("Original ZIO race", runOriginalZioRace(), measurementRuns)
      println(f"\nOriginal ZIO race average: ${originalZioTime/1000000.0}%.2f ms, ${calculateOps(originalZioTime)}%.2f ops/sec")
    } catch {
      case e: Exception => 
        println(s"Original ZIO benchmark error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    // Measure optimized ZIO race
    var optimizedZioTime: Long = 0
    try {
      optimizedZioTime = runBenchmark("Optimized ZIO race", runOptimizedZioRace(), measurementRuns)
      println(f"\nOptimized ZIO race average: ${optimizedZioTime/1000000.0}%.2f ms, ${calculateOps(optimizedZioTime)}%.2f ops/sec")
    } catch {
      case e: Exception => 
        println(s"Optimized ZIO benchmark error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    if (catsTime > 0 && originalZioTime > 0 && optimizedZioTime > 0) {
      // Calculate performance ratios
      val catsToOriginalRatio = catsTime.toDouble / originalZioTime.toDouble
      val catsToOptimizedRatio = catsTime.toDouble / optimizedZioTime.toDouble
      val originalToOptimizedRatio = originalZioTime.toDouble / optimizedZioTime.toDouble
      
      println("\nPerformance ratios:")
      println(f"Cats-Effect / Original ZIO = $catsToOriginalRatio%.2fx")
      println(f"Cats-Effect / Optimized ZIO = $catsToOptimizedRatio%.2fx")
      println(f"Original ZIO / Optimized ZIO = $originalToOptimizedRatio%.2fx")
      
      // Calculate throughput improvements
      val originalToCatsOpsRatio = calculateOps(originalZioTime) / calculateOps(catsTime)
      val optimizedToCatsOpsRatio = calculateOps(optimizedZioTime) / calculateOps(catsTime)
      val optimizedToOriginalOpsRatio = calculateOps(optimizedZioTime) / calculateOps(originalZioTime)
      
      println("\nThroughput ratios:")
      println(f"Original ZIO / Cats-Effect = $originalToCatsOpsRatio%.2fx")
      println(f"Optimized ZIO / Cats-Effect = $optimizedToCatsOpsRatio%.2fx")
      println(f"Optimized ZIO / Original ZIO = $optimizedToOriginalOpsRatio%.2fx")
      
      // Verify if the 5x performance goal has been achieved
      println("\nVerification result:")
      if (catsToOptimizedRatio >= 5.0) {
        println("✅ SUCCESS: The optimized race implementation achieves the 5x performance improvement goal!")
        println(f"   Actual improvement: ${catsToOptimizedRatio}%.2fx (goal: 5x)")
      } else {
        println("❌ INCOMPLETE: The optimized race implementation does not yet achieve the 5x performance improvement goal.")
        println(f"   Current improvement: ${catsToOptimizedRatio}%.2fx (goal: 5x)")
      }
    } else {
      println("\nCould not calculate performance ratios due to benchmark errors.")
    }
    
    println("\nBenchmark complete!")
  }
}

// Run the benchmark when this script is executed
RaceOptimizationBenchmark.main(Array())