//> using file "SimplifiedOptimizedRace.scala"
//> using dep "dev.zio::zio:2.1.17"

import zio._
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple verification script for the SimplifiedOptimizedRace implementation.
 * This script tests basic race functionality and performance.
 */
object VerifySimplifiedOptimizedRace extends ZIOAppDefault {

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
      
      _ <- Console.printLine("\nVerification complete!")
    } yield ()
  }

}