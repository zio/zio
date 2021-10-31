package zio.stream.experimental

import zio._
import zio.test._
import zio.test.Assertion._

object ZPipelineSpec extends ZIOBaseSpec {
  val spec: ZSpec[Environment, Failure] =
    suite("ZPipelineSpec")(
      test("groupAdjacentBy")(check(Gen.chunkOf(Gen.chunkOf(Gen.int))) { iss =>
        val keyFn    = (x: Int) => x % 2 == 0
        val pipeline = ZPipeline.groupAdjacentBy(keyFn)

        pipeline(ZStream.fromChunk(iss).flattenChunks).runCollect.map { oss =>
          val splat = oss.foldLeft[Chunk[Int]](Chunk.empty) { case (acc, (_, is)) => acc ++ is.toChunk }

          def verifyInside(in: Chunk[(Boolean, NonEmptyChunk[Int])]) =
            in.map { case (k, xs) => xs.forall(keyFn(_) == k) }

          def verifyAdjacent(in: Chunk[(Boolean, Any)]): Boolean =
            in.sliding(2, 1).foldLeft(true)((res, chunk) => res && (chunk.length == 1 || chunk(0) != chunk(1)))

          assert(splat)(equalTo(iss.flatten)) &&
          assert(verifyInside(oss))(forall(isTrue)) &&
          assert(verifyAdjacent(oss))(isTrue)
        }
      })
    )
}
