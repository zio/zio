//> using lib "dev.zio::zio:2.1.17"
//> using file "OptimizedRace.scala"

import zio._

/**
 * Simple test to verify that the OptimizedRace implementation works correctly
 */
object SimpleRaceTest extends ZIOAppDefault {
  def run = {
    for {
      _ <- Console.printLine("=== Testing OptimizedRace Implementation ===")
      
      // Test 1: Basic race functionality - right side wins
      _ <- Console.printLine("\nTest 1: Right side wins")
      rightResult <- testRightWins
      _ <- Console.printLine(s"Result: $rightResult")
      
      // Test 2: Basic race functionality - left side wins
      _ <- Console.printLine("\nTest 2: Left side wins")
      leftResult <- testLeftWins
      _ <- Console.printLine(s"Result: $leftResult")
      
      _ <- Console.printLine("\nAll tests completed successfully!")
    } yield ()
  }
  
  def testRightWins: Task[String] = {
    implicit val trace = Trace.empty
    OptimizedRace.race(
      ZIO.never,
      ZIO.succeed("Right side won")
    )
  }
  
  def testLeftWins: Task[String] = {
    implicit val trace = Trace.empty
    OptimizedRace.race(
      ZIO.succeed("Left side won"),
      ZIO.never
    )
  }
}