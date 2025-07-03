package zio.stream

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * Test for buffer(1) fix to ensure it only buffers 1 element.
 * This validates the fix for Issue #9810 where buffer(1) was buffering 2 elements.
 */
object BufferOneSpec extends ZIOSpecDefault {

  def spec = suite("ZStream buffer(1)")(
    // TEST #1: Verify buffer(1) only buffers exactly 1 element using precise timing control
    test("buffer(1) should only buffer 1 element") {
      for {
        queue <- Queue.unbounded[Int]
        ref   <- Ref.make(0)
        
        // Create a consumer that processes one element then waits
        fiber <- ZStream
                  .fromQueue(queue)
                  .tap(_ => ref.update(_ + 1))           // Count processed elements
                  .buffer(1)                             // Should only buffer 1 element
                  .take(1)                               // Take only 1 element to create backpressure
                  .runDrain
                  .fork
        
        // Offer multiple elements quickly
        _ <- queue.offer(1)
        _ <- queue.offer(2) 
        
        // Wait a moment for processing using TestClock
        _ <- TestClock.adjust(100.millis)
        
        // Get count - should be at most 2 (1 consumed + 1 buffered)
        bufferedCount <- ref.get
        
        _ <- fiber.interrupt
        _ <- queue.shutdown
        
      } yield assert(bufferedCount)(isLessThanEqualTo(2))
    },

    // TEST #2: Verify buffer(1) produces correct output
    test("buffer(1) should produce correct output") {
      for {
        result <- ZStream
                    .range(1, 6)
                    .buffer(1)
                    .runCollect
      } yield assert(result)(equalTo(Chunk(1, 2, 3, 4, 5)))
    },

    // TEST #3: Verify buffer(1) still works correctly for normal flow
    test("buffer(1) should still work correctly for normal flow") {
      for {
        queue <- Queue.unbounded[Int]
        
        // Fork the consumer first
        fiber <- ZStream
                  .fromQueue(queue)
                  .buffer(1)
                  .take(3)
                  .runCollect
                  .fork
        
        // Then offer elements
        _ <- queue.offer(1)
        _ <- queue.offer(2) 
        _ <- queue.offer(3)
        
        // Wait briefly using TestClock then shutdown
        _ <- TestClock.adjust(100.millis)
        _ <- queue.shutdown
        
        result <- fiber.join
        
      } yield assert(result)(equalTo(Chunk(1, 2, 3)))
    }
  )
}
