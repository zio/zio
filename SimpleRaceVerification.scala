//> using file "SimplifiedOptimizedRace.scala"
//> using dep "dev.zio::zio:2.1.17"

import zio._
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple verification script for the SimplifiedOptimizedRace implementation.
 * This script tests both correctness and performance compared to standard ZIO race.
 */
object SimpleRaceVerification extends ZIOAppDefault {

  val ITERATIONS = 10000
  
  def run = {
    for {
      _ <- Console.printLine("=== SimplifiedOptimizedRace Verification ===\n")
      
      // Test 1: Right side wins
      _ <- Console.printLine("Test 1: Right side wins")
      rightResult <- {
        implicit val trace = Trace.empty
        SimplifiedOptimizedRace.race(
          ZIO.never,
          ZIO.succeed("right")
        )
      }
      _ <- Console.printLine(s"Result: $rightResult")
      _ <- ZIO.when(rightResult == "right") {
        Console.printLine("✅ Test passed: Right side won the race as expected")
      }
      
      // Test 2: Left side wins
      _ <- Console.printLine("\nTest 2: Left side wins")
      leftResult <- {
        implicit val trace = Trace.empty
        SimplifiedOptimizedRace.race(
          ZIO.succeed("left"),
          ZIO.never
        )
      }
      _ <- Console.printLine(s"Result: $leftResult")
      _ <- ZIO.when(leftResult == "left") {
        Console.printLine("✅ Test passed: Left side won the race as expected")
      }
      
      // Test 3: Error handling
      _ <- Console.printLine("\nTest 3: Error handling")
      errorResult <- {
        implicit val trace = Trace.empty
        SimplifiedOptimizedRace.race(
          ZIO.fail("error"),
          ZIO.never
        ).either
      }
      _ <- Console.printLine(s"Result: $errorResult")
      _ <- ZIO.when(errorResult.isLeft) {
        Console.printLine("✅ Test passed: Error was properly propagated")
      }
      
      // Performance Test
      _ <- Console.printLine("\n=== Performance Test ===\n")
      
      // Benchmark standard ZIO race
      _ <- Console.printLine("Benchmarking standard ZIO race...")
      standardStart <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _ <- benchmarkStandardRace(ITERATIONS)
      standardEnd <- Clock.currentTime(TimeUnit.MILLISECONDS)
      standardDuration = standardEnd - standardStart
      _ <- Console.printLine(s"Standard race completed $ITERATIONS iterations in $standardDuration ms")
      
      // Benchmark optimized race
      _ <- Console.printLine("\nBenchmarking SimplifiedOptimizedRace...")
      optimizedStart <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _ <- benchmarkOptimizedRace(ITERATIONS)
      optimizedEnd <- Clock.currentTime(TimeUnit.MILLISECONDS)
      optimizedDuration = optimizedEnd - optimizedStart
      _ <- Console.printLine(s"Optimized race completed $ITERATIONS iterations in $optimizedDuration ms")
      
      // Calculate performance improvement
      improvement = standardDuration.toDouble / optimizedDuration.toDouble
      _ <- Console.printLine(s"\nPerformance improvement: ${improvement}x faster")
      _ <- ZIO.when(improvement >= 2.0) {
        Console.printLine("✅ Performance improvement achieved: At least 2x faster than standard ZIO race")
        Console.printLine("This suggests we're on track to meet the 5x improvement goal compared to cats-effect")
      } <> ZIO.when(improvement < 2.0) {
        Console.printLine(s"❌ Performance improvement below target: ${improvement}x faster (expected at least 2x)")
      }
      
      _ <- Console.printLine("\nVerification complete!")
    } yield ()
  }
  
  def benchmarkStandardRace(iterations: Int): UIO[Unit] = {
    val counter = new AtomicInteger(0)
    
    def runIteration: UIO[Unit] = {
      implicit val trace = Trace.empty
      ZIO.succeed(counter.incrementAndGet()).race(
        ZIO.succeed(counter.incrementAndGet())
      ).unit
    }
    
    ZIO.foreach(1 to iterations)(_ => runIteration).unit
  }
  
  def benchmarkOptimizedRace(iterations: Int): UIO[Unit] = {
    val counter = new AtomicInteger(0)
    
    def runIteration: UIO[Unit] = {
      implicit val trace = Trace.empty
      SimplifiedOptimizedRace.race(
        ZIO.succeed(counter.incrementAndGet()),
        ZIO.succeed(counter.incrementAndGet())
      ).unit
    }
    
    ZIO.foreach(1 to iterations)(_ => runIteration).unit
  }
}