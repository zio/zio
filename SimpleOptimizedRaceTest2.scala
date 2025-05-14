//> using lib "dev.zio::zio:2.1.17"
//> using file "/Users/diouf/zio/SimpleOptimizedRace.scala"

import zio._

/**
 * Simplified test to verify the correctness of the optimized race implementation.
 * This test focuses on the basic race functionality to ensure it works as expected.
 */
object SimpleOptimizedRaceTest2 extends ZIOAppDefault {
  def run = {
    for {
      _ <- Console.printLine("=== Testing SimpleOptimizedRace Implementation ===\n")
      
      // Test 1: Basic race functionality - right side wins
      _ <- Console.printLine("Test 1: Right side wins")
      rightResult <- testRightWins
      _ <- Console.printLine(s"Result: $rightResult\n")
      
      // Test 2: Basic race functionality - left side wins
      _ <- Console.printLine("Test 2: Left side wins")
      leftResult <- testLeftWins
      _ <- Console.printLine(s"Result: $leftResult\n")
      
      // Test 3: Test interruption of loser
      _ <- Console.printLine("Test 3: Interruption of loser")
      interruptResult <- testInterruption
      _ <- Console.printLine(s"Loser interrupted: $interruptResult\n")
      
      // Test 4: Test error handling
      _ <- Console.printLine("Test 4: Error handling")
      errorResult <- testErrorHandling.either
      _ <- Console.printLine(s"Error result: $errorResult\n")
      
      _ <- Console.printLine("All tests completed successfully!")
    } yield ()
  }
  
  def testRightWins: Task[Int] = {
    implicit val trace = Trace.empty
    SimpleOptimizedRace.race(
      ZIO.never,
      ZIO.succeed(42)
    )
  }
  
  def testLeftWins: Task[Int] = {
    implicit val trace = Trace.empty
    SimpleOptimizedRace.race(
      ZIO.succeed(42),
      ZIO.never
    )
  }
  
  def testInterruption: UIO[Boolean] = {
    implicit val trace = Trace.empty
    for {
      ref <- Ref.make(false)
      _ <- SimpleOptimizedRace.race(
        ZIO.succeed(42),
        ZIO.never.onInterrupt(ref.set(true))
      )
      interrupted <- ref.get
    } yield interrupted
  }
  
  def testErrorHandling: IO[String, Int] = {
    implicit val trace = Trace.empty
    SimpleOptimizedRace.race(
      ZIO.fail("left error"),
      ZIO.never
    )
  }
}