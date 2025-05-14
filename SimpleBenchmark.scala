//> using lib "org.typelevel::cats-effect:3.6.1"
//> using lib "dev.zio::zio:2.1.17"
//> using file "SimpleOptimizedRace.scala"

import zio._
import cats.effect.unsafe.implicits.global

/**
 * Simplified benchmark to verify if SimpleOptimizedRace achieves the 5x performance goal
 * compared to cats-effect.
 */
object SimpleBenchmark extends ZIOAppDefault {
  // Number of iterations for each test run
  val iterations = 100000
  
  def run = {
    for {
      _ <- Console.printLine("=== SimpleOptimizedRace Benchmark ===\n")
      
      // Run cats-effect race benchmark
      _ <- Console.printLine("Running Cats-effect race benchmark...")
      catsStart <- ZIO.succeed(System.nanoTime())
      _ <- ZIO.attempt {
        import cats.effect.IO
        
        def loop(i: Int): IO[Int] =
          if (i < iterations) IO.race(IO.never, IO.pure(i + 1)).flatMap(_ => loop(i + 1))
          else IO.pure(i)
        
        loop(0).unsafeRunSync()
      }
      catsEnd <- ZIO.succeed(System.nanoTime())
      val catsDuration = catsEnd - catsStart
      val catsOps = iterations.toDouble / (catsDuration.toDouble / 1_000_000_000.0)
      
      // Run standard ZIO race benchmark
      _ <- Console.printLine("\nRunning Standard ZIO race benchmark...")
      zioStart <- ZIO.succeed(System.nanoTime())
      _ <- ZIO.attempt {
        def loop(i: Int): UIO[Int] =
          if (i < iterations) ZIO.never.race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
          else ZIO.succeed(i)
        
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
        }
      }
      zioEnd <- ZIO.succeed(System.nanoTime())
      val zioDuration = zioEnd - zioStart
      val zioOps = iterations.toDouble / (zioDuration.toDouble / 1_000_000_000.0)
      
      // Run optimized race benchmark
      _ <- Console.printLine("\nRunning SimpleOptimizedRace benchmark...")
      optimizedStart <- ZIO.succeed(System.nanoTime())
      _ <- ZIO.attempt {
        def loop(i: Int): UIO[Int] =
          if (i < iterations) SimpleOptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
          else ZIO.succeed(i)
        
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
        }
      }
      optimizedEnd <- ZIO.succeed(System.nanoTime())
      val optimizedDuration = optimizedEnd - optimizedStart
      val optimizedOps = iterations.toDouble / (optimizedDuration.toDouble / 1_000_000_000.0)
      
      // Calculate performance ratios
      val standardZioVsCatsRatio = catsDuration.toDouble / zioDuration.toDouble
      val optimizedVsCatsRatio = catsDuration.toDouble / optimizedDuration.toDouble
      val optimizedVsStandardRatio = zioDuration.toDouble / optimizedDuration.toDouble
      
      // Calculate ops/sec ratios
      val catsVsStandardZioOpsRatio = zioOps / catsOps
      val catsVsOptimizedOpsRatio = optimizedOps / catsOps
      val optimizedVsStandardOpsRatio = optimizedOps / zioOps
      
      // Print results
      _ <- Console.printLine("\n=== Benchmark Results ===\n")
      _ <- Console.printLine(f"Cats-effect race: ${catsDuration/1000000.0}%.2f ms, $catsOps%.2f ops/sec")
      _ <- Console.printLine(f"Standard ZIO race: ${zioDuration/1000000.0}%.2f ms, $zioOps%.2f ops/sec")
      _ <- Console.printLine(f"SimpleOptimizedRace: ${optimizedDuration/1000000.0}%.2f ms, $optimizedOps%.2f ops/sec")
      
      _ <- Console.printLine("\n=== Performance Ratios (time) ===\n")
      _ <- Console.printLine(f"Cats-effect / Standard ZIO = $standardZioVsCatsRatio%.2fx (higher means ZIO is faster)")
      _ <- Console.printLine(f"Cats-effect / SimpleOptimizedRace = $optimizedVsCatsRatio%.2fx (higher means optimized is faster)")
      _ <- Console.printLine(f"Standard ZIO / SimpleOptimizedRace = $optimizedVsStandardRatio%.2fx (higher means optimized is faster)")
      
      _ <- Console.printLine("\n=== Performance Ratios (ops/sec) ===\n")
      _ <- Console.printLine(f"Standard ZIO / Cats-effect = $catsVsStandardZioOpsRatio%.2fx (higher means faster)")
      _ <- Console.printLine(f"SimpleOptimizedRace / Cats-effect = $catsVsOptimizedOpsRatio%.2fx (higher means faster)")
      _ <- Console.printLine(f"SimpleOptimizedRace / Standard ZIO = $optimizedVsStandardOpsRatio%.2fx (higher means faster)")
      
      // Check if the 5x performance goal was achieved
      val optimizedGoalAchieved = catsVsOptimizedOpsRatio >= 5.0
      _ <- Console.printLine("\n=== Performance Goal Analysis ===\n")
      _ <- Console.printLine(f"Performance goal of 5x improvement over cats-effect: ${if (optimizedGoalAchieved) "ACHIEVED" else "NOT ACHIEVED"}")
      _ <- Console.printLine(f"Actual improvement over cats-effect: $catsVsOptimizedOpsRatio%.2fx")
      _ <- Console.printLine(f"Improvement over standard ZIO implementation: $optimizedVsStandardOpsRatio%.2fx")
      
      _ <- Console.printLine("\nBenchmark complete!")
    } yield ()
  }
}