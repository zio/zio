//> using lib "org.typelevel::cats-effect:3.6.1"
//> using lib "dev.zio::zio:2.1.17"
//> using file "SimpleOptimizedRace.scala"

import zio._
import zio.Console._
import cats.effect.unsafe.implicits.global

/**
 * Simple test to verify if the SimpleOptimizedRace implementation achieves the 5x performance goal
 * compared to cats-effect. This benchmark focuses on the scenario where one side completes immediately
 * while the other never completes, which is the critical case for race performance.
 */
object SimpleRacePerformanceVerification extends ZIOAppDefault {
  // Number of iterations for each test run
  val iterations = 100000
  // Number of warmup runs before actual measurement
  val warmupRuns = 3
  // Number of measurement runs to average
  val measurementRuns = 5
  
  def run = {
    for {
      _ <- printLine("=== SimpleOptimizedRace Verification Benchmark ===\n")
      _ <- printLine(s"Iterations per run: $iterations")
      _ <- printLine(s"Warmup runs: $warmupRuns")
      _ <- printLine(s"Measurement runs: $measurementRuns")
      _ <- printLine("\nStarting benchmarks...\n")
      
      // Run cats-effect race benchmark
      _ <- printLine("Running Cats-effect race benchmark...")
      catsDurations <- ZIO.foreach(1 to (warmupRuns + measurementRuns)) { run =>
        ZIO.attempt {
          import cats.effect.IO
          
          val startTime = System.nanoTime()
          
          def loop(i: Int): IO[Int] =
            if (i < iterations) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
            else IO.pure(i)
          
          loop(0).unsafeRunSync()
          
          val endTime = System.nanoTime()
          val duration = endTime - startTime
          val opsPerSec = iterations.toDouble / (duration.toDouble / 1_000_000_000.0)
          
          if (run <= warmupRuns) {
            println(f"  Warmup $run/$warmupRuns: ${duration.toDouble/1000000.0}%.2f ms, $opsPerSec%.2f ops/sec")
          } else {
            println(f"  Run ${run-warmupRuns}/$measurementRuns: ${duration.toDouble/1000000.0}%.2f ms, $opsPerSec%.2f ops/sec")
          }
          
          duration
        }
      }
      val catsDuration = catsDurations.drop(warmupRuns).map(_.toDouble).sum / measurementRuns.toDouble
      val catsOps = iterations.toDouble / (catsDuration / 1_000_000_000.0)
      
      // Run standard ZIO race benchmark
      _ <- printLine("\nRunning Standard ZIO race benchmark...")
      zioDurations <- ZIO.foreach(1 to (warmupRuns + measurementRuns)) { run =>
        ZIO.attempt {
          val startTime = System.nanoTime()
          
          def loop(i: Int): UIO[Int] =
            if (i < iterations) ZIO.never.race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
            else ZIO.succeed(i)
          
          Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
          }
          
          val endTime = System.nanoTime()
          val duration = endTime - startTime
          val opsPerSec = iterations.toDouble / (duration.toDouble / 1_000_000_000.0)
          
          if (run <= warmupRuns) {
            println(f"  Warmup $run/$warmupRuns: ${duration.toDouble/1000000.0}%.2f ms, $opsPerSec%.2f ops/sec")
          } else {
            println(f"  Run ${run-warmupRuns}/$measurementRuns: ${duration.toDouble/1000000.0}%.2f ms, $opsPerSec%.2f ops/sec")
          }
          
          duration
        }
      }
      val zioDuration = zioDurations.drop(warmupRuns).map(_.toDouble).sum / measurementRuns.toDouble
      val zioOps = iterations.toDouble / (zioDuration / 1_000_000_000.0)
      
      // Run optimized race benchmark
      _ <- printLine("\nRunning SimpleOptimizedRace benchmark...")
      optimizedDurations <- ZIO.foreach(1 to (warmupRuns + measurementRuns)) { run =>
        ZIO.attempt {
          val startTime = System.nanoTime()
          
          def loop(i: Int): UIO[Int] =
            if (i < iterations) SimpleOptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
            else ZIO.succeed(i)
          
          Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
          }
          
          val endTime = System.nanoTime()
          val duration = endTime - startTime
          val opsPerSec = iterations.toDouble / (duration.toDouble / 1_000_000_000.0)
          
          if (run <= warmupRuns) {
            println(f"  Warmup $run/$warmupRuns: ${duration.toDouble/1000000.0}%.2f ms, $opsPerSec%.2f ops/sec")
          } else {
            println(f"  Run ${run-warmupRuns}/$measurementRuns: ${duration.toDouble/1000000.0}%.2f ms, $opsPerSec%.2f ops/sec")
          }
          
          duration
        }
      }
      val optimizedDuration = optimizedDurations.drop(warmupRuns).map(_.toDouble).sum / measurementRuns.toDouble
      val optimizedOps = iterations.toDouble / (optimizedDuration / 1_000_000_000.0)
      
      // Calculate performance ratios
      val standardZioVsCatsRatio = catsDuration / zioDuration
      val optimizedVsCatsRatio = catsDuration / optimizedDuration
      val optimizedVsStandardRatio = zioDuration / optimizedDuration
      
      // Calculate ops/sec ratios
      val catsVsStandardZioOpsRatio = zioOps / catsOps
      val catsVsOptimizedOpsRatio = optimizedOps / catsOps
      val optimizedVsStandardOpsRatio = optimizedOps / zioOps
      
      // Print results
      _ <- printLine("\n=== Benchmark Results ===\n")
      _ <- printLine(f"Cats-effect race: ${catsDuration/1000000.0}%.2f ms, $catsOps%.2f ops/sec")
      _ <- printLine(f"Standard ZIO race: ${zioDuration/1000000.0}%.2f ms, $zioOps%.2f ops/sec")
      _ <- printLine(f"SimpleOptimizedRace: ${optimizedDuration/1000000.0}%.2f ms, $optimizedOps%.2f ops/sec")
      
      _ <- printLine("\n=== Performance Ratios (time) ===\n")
      _ <- printLine(f"Cats-effect / Standard ZIO = $standardZioVsCatsRatio%.2fx (higher means ZIO is faster)")
      _ <- printLine(f"Cats-effect / SimpleOptimizedRace = $optimizedVsCatsRatio%.2fx (higher means optimized is faster)")
      _ <- printLine(f"Standard ZIO / SimpleOptimizedRace = $optimizedVsStandardRatio%.2fx (higher means optimized is faster)")
      
      _ <- printLine("\n=== Performance Ratios (ops/sec) ===\n")
      _ <- printLine(f"Standard ZIO / Cats-effect = $catsVsStandardZioOpsRatio%.2fx (higher means faster)")
      _ <- printLine(f"SimpleOptimizedRace / Cats-effect = $catsVsOptimizedOpsRatio%.2fx (higher means faster)")
      _ <- printLine(f"SimpleOptimizedRace / Standard ZIO = $optimizedVsStandardOpsRatio%.2fx (higher means faster)")
      
      // Check if the 5x performance goal was achieved
      val optimizedGoalAchieved = catsVsOptimizedOpsRatio >= 5.0
      _ <- printLine("\n=== Performance Goal Analysis ===\n")
      _ <- printLine(f"Performance goal of 5x improvement over cats-effect: ${if (optimizedGoalAchieved) "ACHIEVED" else "NOT ACHIEVED"}")
      _ <- printLine(f"Actual improvement over cats-effect: $catsVsOptimizedOpsRatio%.2fx")
      _ <- printLine(f"Improvement over standard ZIO implementation: $optimizedVsStandardOpsRatio%.2fx")
      
      _ <- printLine("\nBenchmark complete!")
    } yield ()
  }
}