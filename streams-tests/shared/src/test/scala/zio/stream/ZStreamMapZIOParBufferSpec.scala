package zio.stream

import zio._
import zio.test._

object ZStreamMapZIOParBufferSpec extends ZIOBaseSpec {

  def spec =
    suite("ZStreamMapZIOParBufferSpec")(
      test("mapZIOPar honors parallelism > 16 without explicitly specifying bufferSize (#9339)") {
        val parallelism = 32
        for {
          latch  <- Promise.make[Nothing, Unit]
          active <- Ref.make(0)
          result <- ZStream
                      .fromIterable(1 to parallelism)
                      .mapZIOPar(parallelism) { i =>
                        active.updateAndGet(_ + 1).flatMap { count =>
                          ZIO.when(count == parallelism)(latch.succeed(())) *>
                            latch.await.as(i * 2)
                        }
                      }
                      .runCollect
        } yield assertTrue(result == Chunk.fromIterable((1 to parallelism).map(_ * 2)))
      },
      test("mapZIOPar caps default bufferSize to prevent OOM when parallelism is Int.MaxValue (#9339)") {
        for {
          result <- ZStream.fromIterable(1 to 50).mapZIOPar(Int.MaxValue)(i => ZIO.succeed(i + 1)).runCollect
        } yield assertTrue(result == Chunk.fromIterable(2 to 51))
      }
    )
}
