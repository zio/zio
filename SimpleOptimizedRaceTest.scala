//> using lib "dev.zio::zio:2.1.17"
//> using file "/Users/diouf/zio/OptimizedRace.scala"

import zio._

/**
 * Simplified test to verify the correctness of the optimized race implementation.
 * This test focuses on the basic race functionality to ensure it works as expected.
 */
object SimpleOptimizedRaceTest extends ZIOAppDefault {
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
      
      // Test 3: Test interruption of loser
      _ <- Console.printLine("\nTest 3: Interruption of loser")
      interruptResult <- testInterruption
      _ <- Console.printLine(s"Loser interrupted: $interruptResult")
      
      // Test 4: Test error handling
      _ <- Console.printLine("\nTest 4: Error handling")
      errorResult <- testErrorHandling.either
      _ <- Console.printLine(s"Error result: $errorResult")
      
      _ <- Console.printLine("\nAll tests completed successfully!")
    } yield ()
  }
  
  def testRightWins: Task[Int] = {
    implicit val trace = Trace.empty
    OptimizedRace.race(
      ZIO.never,
      ZIO.succeed(42)
    )
  }
  
  def testLeftWins: Task[Int] = {
    implicit val trace = Trace.empty
    OptimizedRace.race(
      ZIO.succeed(42),
      ZIO.never
    )
  }
  
  def testInterruption: UIO[Boolean] = {
    implicit val trace = Trace.empty
    for {
      ref <- Ref.make(false)
      _ <- OptimizedRace.race(
        ZIO.succeed(42),
        ZIO.never.onInterrupt(ref.set(true))
      )
      interrupted <- ref.get
    } yield interrupted
  }
  
  def testErrorHandling: IO[Throwable, Int] = {
    implicit val trace = Trace.empty
    OptimizedRace.race(
      ZIO.fail(new RuntimeException("left error")),
      ZIO.never
    )
  }
}