//> using lib "org.typelevel::cats-effect:3.6.1"
//> using lib "dev.zio::zio:2.1.17"
//> using file "SimplifiedOptimizedRace.scala"

import zio._
import cats.effect.unsafe.implicits.global
import java.util.concurrent.TimeUnit
import scala.math.Fractional.Implicits.infixFractionalOps

/**
 * Simple test to verify if the SimplifiedOptimizedRace implementation achieves better performance
 * compared to standard ZIO race and cats-effect race.
 */
object SimplifiedRaceTest extends ZIOAppDefault {
  // Number of iterations for each test run
  val iterations = 10000
  
  def run = {
    for {
      _ <- Console.printLine("=== SimplifiedOptimizedRace Performance Test ===\n")
      _ <- Console.printLine(s"Iterations: $iterations\n")
      
      // Test cats-effect race
      _ <- Console.printLine("Testing cats-effect race...")
      catsDuration <- ZIO.attempt {
        import cats.effect.IO
        
        val startTime = System.nanoTime()
        
        def loop(i: Int): IO[Int] =
          if (i < iterations) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
          else IO.pure(i)
        
        loop(0).unsafeRunSync()
        
        val endTime = System.nanoTime()
        endTime - startTime
      }
      catsOps = iterations / (catsDuration / 1_000_000_000.0)
      _ <- Console.printLine(f"Cats-effect race: ${catsDuration/1000000.0}%.2f ms, $catsOps%.2f ops/sec\n")
      
      // Test standard ZIO race
      _ <- Console.printLine("Testing standard ZIO race...")
      zioDuration <- ZIO.attempt {
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
      zioOps = iterations / (zioDuration / 1_000_000_000.0)
      _ <- Console.printLine(f"Standard ZIO race: ${zioDuration/1000000.0}%.2f ms, $zioOps%.2f ops/sec\n")
      
      // Test our optimized race implementation
      _ <- Console.printLine("Testing SimplifiedOptimizedRace...")
      optimizedDuration <- ZIO.attempt {
        val startTime = System.nanoTime()
        
        def loop(i: Int): UIO[Int] = {
          if (i < iterations) {
            // Use explicit Trace.empty for Scala 3 compatibility
            given Trace = Trace.empty
            SimplifiedOptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
          } else ZIO.succeed(i)
        }
        
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
        }
        
        val endTime = System.nanoTime()
        endTime - startTime
      }
      optimizedOps = iterations / (optimizedDuration / 1_000_000_000.0)
      _ <- Console.printLine(f"SimplifiedOptimizedRace: ${optimizedDuration/1000000.0}%.2f ms, $optimizedOps%.2f ops/sec\n")
      
      // Calculate performance ratios
      zioVsCatsRatio = zioOps / catsOps
      optimizedVsCatsRatio = optimizedOps / catsOps
      optimizedVsZioRatio = optimizedOps / zioOps
      
      // Print performance ratios
      _ <- Console.printLine("=== Performance Ratios (ops/sec) ===\n")
      _ <- Console.printLine(f"Standard ZIO / Cats-effect = ${zioVsCatsRatio}%.2fx")
      _ <- Console.printLine(f"SimplifiedOptimizedRace / Cats-effect = ${optimizedVsCatsRatio}%.2fx")
      _ <- Console.printLine(f"SimplifiedOptimizedRace / Standard ZIO = ${optimizedVsZioRatio}%.2fx\n")
      
      // Check if the 5x performance goal was achieved
      _ <- Console.printLine("=== Performance Goal Analysis ===\n")
      _ <- Console.printLine(f"Performance goal of 5x improvement over cats-effect: ${if (optimizedVsCatsRatio >= 5.0) "ACHIEVED" else "NOT ACHIEVED"}")
      _ <- Console.printLine(f"Actual improvement over cats-effect: ${optimizedVsCatsRatio}%.2fx")
      _ <- Console.printLine(f"Improvement over standard ZIO implementation: ${optimizedVsZioRatio}%.2fx")
    } yield ()
  }
}