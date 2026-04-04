package zio.stream

import zio._
import zio.stream._
import zio.test._

object RechunkDataLossReproduction extends ZIOSpecDefault {
  def spec = suite("RechunkDataLossReproduction")(
    test("peel with rechunking should not lose data") {
      ZIO.scoped {
        ZStream(1, 2, 3, 4, 5)
          .rechunk(2)
          .peel(
            (ZPipeline.rechunk[Int](1) >>> ZSink.take[Int](1))
          )
          .flatMap { case (taken, remainder) =>
            remainder.run(ZSink.head).map { head =>
              assertTrue(taken == Chunk(1) && head == Some(2))
            }
          }
      }
    } @@ TestAspect.timeout(30.seconds),
    test("ZStream(1, 2, 3, 4, 5).rechunk(2).run((ZPipeline.rechunk(1) >>> ZSink.take(1)) *> ZSink.head)") {
      ZStream(1, 2, 3, 4, 5)
        .rechunk(2)
        .run(
          (ZPipeline.rechunk[Int](1) >>> ZSink.take[Int](1)) *> ZSink.head
        )
        .map(result => assertTrue(result == Some(2)))
    } @@ TestAspect.timeout(30.seconds)
  )
}
