package zio.stream.experimental

import zio._
import zio.stream.ChunkUtils
import zio.test.Assertion.equalTo
import zio.test._

object ZSinkSpec1 extends ZIOBaseSpec {
  def spec = suite("ZSinkSpec")(
    suite("Constructors")(
      testM("head")(
        checkM(Gen.listOf(ChunkUtils.smallChunks(Gen.anyInt))) { chunks: Seq[Chunk[Int]] =>
          val headOpt = ZStream.fromChunks(chunks: _*).run(ZSink.head[Int]).either.map(_.toOption)
          assertM(headOpt)(equalTo(chunks.flatMap(_.toSeq).headOption))
        }
      )
    ),
    suite("Combinators")()
  )
}
