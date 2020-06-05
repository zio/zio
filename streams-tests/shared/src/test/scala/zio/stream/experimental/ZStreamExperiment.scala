package zio.stream.experimental

import zio.ZIOBaseSpec
import zio.test.Assertion.equalTo
import zio.test._

object ZStreamExperiment extends ZIOBaseSpec {

  import ZStreamGen._

  def spec =
    suite("ZStreamExperiment")(
      suite("Combinators")(),
      suite("Constructors")(
        testM("fromChunk")(checkM(tinyChunkOf(Gen.anyInt))(c => assertM(ZStream.fromChunk(c).runCollect)(equalTo(c))))
      )
    )
}
