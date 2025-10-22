package zio.examples

import zio._

/**
 * Functional test demonstrating Promise.become() functionality.
 * This addresses GitHub issue #9877.
 */
object PromiseBecomeExample extends ZIOAppDefault {

  def run = for {
    _ <- Console.printLine("Testing Promise.become() functionality")
    _ <- Console.printLine("======================================")
    
    // Test 1: Basic functionality
    _ <- testBasicBecome()
    
    // Test 2: Error handling
    _ <- testErrorHandling()
    
    // Test 3: Already completed promise
    _ <- testAlreadyCompleted()
    
    // Test 4: Multiple awaiters
    _ <- testMultipleAwaiters()
    
    _ <- Console.printLine("\nAll tests completed successfully!")
  } yield ()

  def testBasicBecome() = for {
    _ <- Console.printLine("\n1. Basic Promise.become() test")
    promise <- Promise.make[String, Int]
    fiber   <- (ZIO.sleep(10.millis) *> ZIO.succeed(42)).fork
    linked  <- promise.become(fiber)
    result  <- promise.await
    _ <- Console.printLine(s"   Linked: $linked, Result: $result")
    _ <- ZIO.succeed(assert(linked && result == 42))
  } yield ()

  def testErrorHandling() = for {
    _ <- Console.printLine("\n2. Error handling test")
    promise <- Promise.make[String, Int]
    fiber   <- ZIO.fail("test error").fork
    linked  <- promise.become(fiber)
    result  <- promise.await.exit
    _ <- Console.printLine(s"   Linked: $linked, Error handled: ${result.isFailure}")
    _ <- ZIO.succeed(assert(linked && result.isFailure))
  } yield ()

  def testAlreadyCompleted() = for {
    _ <- Console.printLine("\n3. Already completed promise test")
    promise <- Promise.make[String, Int]
    _       <- promise.succeed(100)
    fiber   <- ZIO.succeed(42).fork
    linked  <- promise.become(fiber)
    result  <- promise.await
    _ <- Console.printLine(s"   Linked: $linked, Result: $result")
    _ <- ZIO.succeed(assert(!linked && result == 100))
  } yield ()

  def testMultipleAwaiters() = for {
    _ <- Console.printLine("\n4. Multiple awaiters test")
    promise <- Promise.make[String, Int]
    fiber   <- (ZIO.sleep(20.millis) *> ZIO.succeed(42)).fork
    linked  <- promise.become(fiber)
    results <- ZIO.collectAllPar(List.fill(5)(promise.await))
    _ <- Console.printLine(s"   Linked: $linked, All results: ${results.forall(_ == 42)}")
    _ <- ZIO.succeed(assert(linked && results.forall(_ == 42)))
  } yield ()
}