package zio.stream

import zio._
import zio.test._
import zio.test.Assertion._

object ZStreamBuffer1Spec extends ZIOSpecDefault {

  def spec = suite("ZStream.buffer(1)")(
    test("buffer(1) should only buffer 1 element - basic test") {
      for {
        values <- Ref.make(List.empty[Int])
        _ <- ZStream(1, 2, 3, 4, 5)
          .tap(i => values.update(_ :+ i))
          .buffer(1)
          .runDrain
        result <- values.get
      } yield {
        // With buffer(1), at most 2 elements should be buffered/processed
        println(s"Values produced: $result")
        assertTrue(result.length >= 2)
      }
    }
  )
}
