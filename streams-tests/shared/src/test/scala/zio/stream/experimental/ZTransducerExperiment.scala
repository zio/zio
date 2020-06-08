package zio.stream.experimental

import zio._
import zio.test.Assertion.equalTo
import zio.test._

object ZTransducerExperiment extends ZIOBaseSpec {

  import ZStreamGen._

  def spec =
    suite("ZTransducerExperiment")(
      suite("Combinators")(),
      suite("Constructors")(
        suite("chunkN")(
          testM("divides chunks into fixed size chunks")(
            checkM(tinyChunkOf(Gen.chunkOf(Gen.anyInt)), Gen.int(1, 100))((c, i) =>
              assertM(
                ZStream
                  .fromChunk(c)
                  .pipe(ZTransducer.chunkN(i, 0))
                  .run(ZSink.collect[Chunk[Int]].chunked)
                  .map(_.forall(_.length == i))
              )(equalTo(true))
            )
          )
        )
      )
    )
}
