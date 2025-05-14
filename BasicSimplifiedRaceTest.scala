//> using file "SimplifiedOptimizedRace.scala"
//> using dep "dev.zio::zio:2.1.17"

import zio._
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Basic test script for the SimplifiedOptimizedRace implementation.
 * This script tests basic race functionality without complex test frameworks.
 */
object BasicSimplifiedRaceTest extends ZIOAppDefault {
  def run = {
    for {
      _ <- Console.printLine("=== SimplifiedOptimizedRace Basic Test ===\n")
      
      // Test 1: Right side wins
      _ <- Console.printLine("Test 1: Right side wins")
      rightResult <- {
        implicit val trace = Trace.empty
        SimplifiedOptimizedRace.race(
          ZIO.sleep(1.second) *> ZIO.succeed("delayed left"),
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
          ZIO.sleep(1.second) *> ZIO.succeed("delayed right")
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
          ZIO.sleep(1.second) *> ZIO.succeed("delayed right")
        ).either
      }
      _ <- Console.printLine(s"Result: $errorResult")
      _ <- ZIO.when(errorResult.isLeft) {
        Console.printLine("✅ Test passed: Error was properly propagated")
      }
      
      // Simple performance test
      _ <- Console.printLine("\n=== Simple Performance Test ===\n")
      
      // Benchmark standard ZIO race
      _ <- Console.printLine("Benchmarking standard ZIO race...")
      standardStart <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _ <- benchmarkStandardRace(1000)
      standardEnd <- Clock.currentTime(TimeUnit.MILLISECONDS)
      standardDuration = standardEnd - standardStart
      _ <- Console.printLine(s"Standard race completed 1000 iterations in $standardDuration ms")
      
      // Benchmark optimized race
      _ <- Console.printLine("\nBenchmarking SimplifiedOptimizedRace...")
      optimizedStart <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _ <- benchmarkOptimizedRace(1000)
      optimizedEnd <- Clock.currentTime(TimeUnit.MILLISECONDS)
      optimizedDuration = optimizedEnd - optimizedStart
      _ <- Console.printLine(s"Optimized race completed 1000 iterations in $optimizedDuration ms")
      
      // Calculate performance improvement
      improvement = standardDuration.toDouble / optimizedDuration.toDouble
      _ <- Console.printLine(s"\nPerformance improvement: ${improvement}x faster")
      
      _ <- Console.printLine("\nAll tests completed!")
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