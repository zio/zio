package zio.stream.experimental

import zio._
import zio.stream.ChunkUtils
import zio.test.Assertion.equalTo
import zio.test._

object ZSinkSpec extends ZIOBaseSpec {
  def spec = suite("ZSinkSpec")(
    suite("Constructors")(
      testM("head")(
        checkM(Gen.listOf(ChunkUtils.smallChunks(Gen.anyInt))) { chunks: Seq[Chunk[Int]] =>
          val headOpt = ZStream.fromChunks(chunks: _*).run(ZSink.head[Int]).either.map(_.toOption)
          assertM(headOpt)(equalTo(chunks.flatMap(_.toSeq).headOption))
        }
      ),
      testM("last")(
        checkM(Gen.listOf(ChunkUtils.smallChunks(Gen.anyInt))) { chunks: Seq[Chunk[Int]] =>
          val lastOpt = ZStream.fromChunks(chunks: _*).run(ZSink.last[Int])
          assertM(lastOpt)(equalTo(chunks.flatMap(_.toSeq).lastOption))
        }
      )
    ),
    suite("Combinators")()
  )
}
