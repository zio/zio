package zio.stream

import zio._
import zio.test._

object Issue10269Spec extends ZIOBaseSpec {
  def spec = suite("Issue10269Spec")(
    test("rechunking to 1 in the peeling Sink should not break the contract") {
      for {
        result <- ZStream(1, 2, 3, 4, 5)
                    .rechunk(2)
                    .run((ZPipeline.rechunk(1) >>> ZSink.take[Int](1)) *> ZSink.head[Int])
      } yield assertTrue(result == Some(2))
    } @@ TestAspect.timeout(30.seconds),
    test("rechunking to 2 in the peeling Sink should not break the contract") {
      for {
        result <- ZStream(1, 2, 3, 4, 5, 6)
                    .rechunk(4)
                    .run((ZPipeline.rechunk(2) >>> ZSink.take[Int](2)) *> ZSink.head[Int])
      } yield assertTrue(result == Some(3))
    } @@ TestAspect.timeout(30.seconds),
    test("peel with rechunking to 1 should not break the contract") {
      ZIO.scoped {
        ZStream(1, 2, 3, 4, 5)
          .rechunk(2)
          .peel(ZPipeline.rechunk(1) >>> ZSink.take[Int](1))
          .flatMap { case (_, remainder) =>
            remainder.run(ZSink.head[Int])
          }
          .map(result => assertTrue(result == Some(2)))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("remainder should contain elements left in Rechunker buffer") {
      for {
        result <- ZStream(1, 2, 3, 4, 5)
                    .run((ZPipeline.rechunk(10) >>> ZSink.take[Int](3)) *> ZSink.collectAll[Int])
      } yield assertTrue(result == Chunk(4, 5))
    } @@ TestAspect.timeout(30.seconds)
  )
}
