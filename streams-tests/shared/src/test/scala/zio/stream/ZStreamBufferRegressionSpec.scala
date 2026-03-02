package zio.stream

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * Regression test for issue #9810 Verifies that buffer(1) only buffers 1
 * element, not 2
 */
object ZStreamBufferRegressionSpec extends ZIOSpecDefault {

  def spec = suite("ZStream.buffer regression tests for #9810")(
    test("buffer(1) does not run more than one element ahead - exact reproduction") {
      for {
        started <- Ref.make(Chunk.empty[Int])
        stream = ZStream
                   .fromIterable(1 to 3)
                   .mapZIO(i => started.update(_ :+ i).as(i))
                   .buffer(1)

        // Pull first element and check how many have started
        startedAfterFirst <- ZIO.scoped {
                               stream.toPull.flatMap { pull =>
                                 for {
                                   _ <- pull.map(_.head).catchAll(_ => ZIO.dieMessage("Unexpected end of stream"))
                                   _ <- TestClock.adjust(50.millis)
                                   s <- started.get
                                 } yield s
                               }
                             }
      } yield assert(startedAfterFirst.toList)(equalTo(List(1, 2)))
    } @@ TestAspect.timeout(5.seconds),
    test("buffer(2) does not run more than two elements ahead") {
      for {
        started <- Ref.make(Chunk.empty[Int])
        stream = ZStream
                   .fromIterable(1 to 4)
                   .mapZIO(i => started.update(_ :+ i).as(i))
                   .buffer(2)

        startedAfterFirst <- ZIO.scoped {
                               stream.toPull.flatMap { pull =>
                                 for {
                                   _ <- pull.map(_.head).catchAll(_ => ZIO.dieMessage("Unexpected end of stream"))
                                   _ <- TestClock.adjust(50.millis)
                                   s <- started.get
                                 } yield s
                               }
                             }
      } yield assert(startedAfterFirst.toList)(equalTo(List(1, 2, 3)))
    } @@ TestAspect.timeout(5.seconds),
    test("buffer(1) with slow consumer - producer should not race ahead") {
      for {
        processed <- Ref.make(0)
        fiber <- ZStream
                   .fromIterable(1 to 100)
                   .tap(_ => processed.update(_ + 1))
                   .buffer(1)
                   .runForeach(_ => ZIO.sleep(1.hour))
                   .fork

        // Wait a bit and check how many were processed
        _     <- TestClock.adjust(1.second)
        count <- processed.get
        _     <- fiber.interrupt
      } yield assertTrue(count <= 2) // At most 2: 1 in buffer + 1 being processed
    }
  )
}
