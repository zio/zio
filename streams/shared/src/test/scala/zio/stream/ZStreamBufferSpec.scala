package zio.stream

import zio._
import zio.test._
import zio.test.Assertion._

object ZStreamBufferSpec extends ZIOSpecDefault {

  def spec = suite("ZStream.buffer advanced")(
    test("buffer(1) should only buffer 1 element - basic") {
      for {
        ref <- Ref.make(0)
        fiber <- ZStream
          .range(1, 100)
          .tap(_ => ref.update(_ + 1))
          .buffer(1)
          .runHead
          .fork
        _ <- TestClock.adjust(1.second)
        count <- ref.get
      } yield assertTrue(count <= 2)
    },
    test("buffer(1) should not run more than 1 element ahead") {
      for {
        counter <- Ref.make(0)
        stream = ZStream
          .repeatZIO(counter.updateAndGet(_ + 1))
          .take(10)
          .buffer(1)
        
        result <- stream
          .tap(_ => TestClock.adjust(100.millis))
          .take(3)
          .runCollect
        
        finalCount <- counter.get
      } yield assertTrue(finalCount <= 4)
    },
    // Additional edge case tests for differentiation
    test("buffer(1) with error handling") {
      for {
        processed <- Ref.make(List.empty[Int])
        stream = ZStream(1, 2, 3)
          .mapZIO(i => 
            if (i == 2) ZIO.fail(new RuntimeException("error"))
            else processed.update(_ :+ i).as(i)
          )
          .buffer(1)
          .catchAll(_ => ZStream.empty)
        
        result <- stream.runCollect
        finalProcessed <- processed.get
      } yield assertTrue(finalProcessed == List(1))
    },
    test("buffer(1) preserves ordering") {
      for {
        result <- ZStream.fromIterable(1 to 100)
          .buffer(1)
          .runCollect
      } yield assertTrue(result.toList == (1 to 100).toList)
    },
    test("buffer(1) with empty stream") {
      for {
        result <- ZStream.empty.buffer(1).runCollect
      } yield assertTrue(result.isEmpty)
    },
    test("buffer(1) with single element") {
      for {
        result <- ZStream.succeed(42).buffer(1).runHead
      } yield assertTrue(result.contains(42))
    }
  )
}
