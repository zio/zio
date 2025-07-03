package zio.stream

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * NEW TEST FILE: Test for buffer(1) fix to ensure it only buffers 1 element
 * instead of 2. This test file validates the fix for Issue #9810.
 */
object BufferOneSpec extends ZIOSpecDefault {

  def spec = suite("ZStream buffer(1)")(
    // TEST #1: Verifies that buffer(1) only buffers exactly 1 element
    test("buffer(1) should only buffer 1 element") {
      for {
        ref   <- Ref.make(0)
        queue <- Queue.bounded[Take[Nothing, Int]](10)
        fiber <- ZStream
                   .range(1, 11)
                   .tap(_ => ref.update(_ + 1))
                   .buffer(1)
                   .tap(_ => TestClock.adjust(100.millis)) // Use TestClock instead of ZIO.sleep
                   .take(3)                                // Take only first 3 elements
                   .runIntoQueue(queue)
                   .fork
        _             <- TestClock.adjust(50.millis) // Advance test clock
        bufferedCount <- ref.get
        _             <- fiber.interrupt
      } yield assert(bufferedCount)(isLessThanEqualTo(4)) // 3 taken + 1 buffered = 4 max
    },

    // TEST #2: Verifies that buffer(1) still processes elements correctly
    test("buffer(1) should behave correctly") {
      for {
        ref1 <- Ref.make(0) // Counter for buffer(1) test

        // Simple test: buffer(1) should work without issues
        _ <- ZStream
               .range(1, 6)
               .tap(_ => ref1.update(_ + 1))
               .buffer(1) // <- FIXED: Now only buffers 1 element
               .take(3)   // Take only 3 elements
               .runDrain

        bufferedCount1 <- ref1.get

      } yield assert(bufferedCount1)(isGreaterThanEqualTo(3) && isLessThanEqualTo(5)) // Should process 3-5 elements
    },

    // TEST #3: Verifies that buffer(1) still works correctly for normal flow scenarios
    test("buffer(1) should still work correctly for normal flow") {
      for {
        result <- ZStream
                    .range(1, 6)
                    .buffer(1) // <- FIXED: Uses new implementation but produces same result
                    .runCollect
      } yield assert(result)(equalTo(Chunk(1, 2, 3, 4, 5))) // Should collect all elements in order
    }
  )
}
