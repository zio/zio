//> using lib "dev.zio::zio:2.0.15"

import zio._

/**
 * Simple test script to verify that the OptimizedRace implementation works correctly
 * before running the full benchmark.
 */
object VerifyOptimizedRaceImplementation {
  def main(args: Array[String]): Unit = {
    println("=== Verifying OptimizedRace Implementation ===")
    
    // Test 1: Basic race functionality - right side wins
    testRaceRightWins()
    
    // Test 2: Basic race functionality - left side wins
    testRaceLeftWins()
    
    println("\nVerification complete!")
  }
  
  def testRaceRightWins(): Unit = {
    println("\nTest 1: Basic race functionality - right side wins")
    
    val program = for {
      result <- OptimizedRace.race(ZIO.never, ZIO.succeed("Right side won"))
      _ <- Console.printLine(s"Result: $result")
    } yield ()
    
    try {
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(program).getOrThrowFiberFailure()
      }
      println("✅ Test passed: Right side won the race as expected")
    } catch {
      case e: Throwable =>
        println(s"❌ Test failed: ${e.getMessage}")
        e.printStackTrace()
    }
  }
  
  def testRaceLeftWins(): Unit = {
    println("\nTest 2: Basic race functionality - left side wins")
    
    val program = for {
      result <- OptimizedRace.race(ZIO.succeed("Left side won"), ZIO.never)
      _ <- Console.printLine(s"Result: $result")
    } yield ()
    
    try {
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(program).getOrThrowFiberFailure()
      }
      println("✅ Test passed: Left side won the race as expected")
    } catch {
      case e: Throwable =>
        println(s"❌ Test failed: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}

// Run the verification when this script is executed
VerifyOptimizedRaceImplementation.main(Array())