package zio.stream

import zio._
import zio.stream.ZStreamAspect._
import zio.test._
import zio.test.Assertion._

object ZStreamAspectSpec extends ZIOBaseSpec {

  def spec = suite("ZStreamAspectSpec")(
    suite("annotated (single annotation)")(
      test("preserves stream elements") {
        val base   = ZStream(1, 2, 3, 4, 5)
        val aspect = annotated("testKey", "testValue")

        for {
          result <- aspect(base).runCollect
        } yield assert(result)(equalTo(Chunk(1, 2, 3, 4, 5)))
      }
    ),
    suite("annotated (multiple annotations)")(
      test("preserves stream elements") {
        val base   = ZStream("a", "b", "c")
        val aspect = annotated("k1" -> "v1", "k2" -> "v2", "k3" -> "v3")

        for {
          result <- aspect(base).runCollect
        } yield assert(result)(equalTo(Chunk("a", "b", "c")))
      }
    ),
    suite("rechunk")(
      test("splits into chunks of size ≤ n") {
        val base      = ZStream.fromIterable(1 to 7)
        val aspect    = rechunk(3)
        val rechunked = aspect(base)

        for {
          chunks <- rechunked.chunks.runCollect
        } yield {
          val allChunks = chunks.toList
          val flattened = chunks.foldLeft(Chunk.empty[Int])(_ ++ _)
          assert(allChunks.forall(_.size <= 3))(isTrue) &&
          assert(flattened)(equalTo(Chunk.fromIterable(1 to 7)))
        }
      }
    ),
    suite("tagged")(
      test("preserves stream elements") {
        val base   = ZStream(10, 20, 30)
        val aspect = tagged("metric", "value")

        for {
          result <- aspect(base).runCollect
        } yield assert(result)(equalTo(Chunk(10, 20, 30)))
      }
    ),
    suite("composition of aspects")(
      test("annotated >>> rechunk preserves elements and applies rechunking") {
        val base     = ZStream.fromIterable(1 to 5)
        val aspect   = annotated("x", "y") >>> rechunk(2)
        val composed = aspect(base)

        for {
          chunks <- composed.chunks.runCollect
        } yield {
          val allChunks = chunks.toList
          val flattened = chunks.foldLeft(Chunk.empty[Int])(_ ++ _)
          assert(flattened)(equalTo(Chunk.fromIterable(1 to 5))) &&
          assert(allChunks.forall(_.size <= 2))(isTrue)
        }
      },
      test("@@ as alias for >>> works the same") {
        val base     = ZStream.fromIterable(1 to 5)
        val aspect1  = rechunk(2)
        val aspect2  = tagged("k", "v")
        val viaAlias = aspect1 @@ aspect2

        for {
          result <- viaAlias(base).runCollect
        } yield assert(result)(equalTo(Chunk.fromIterable(1 to 5)))
      },
      test("rechunk >>> rechunk(n) is idempotent if n ≥ stream length") {
        val base     = ZStream.fromIterable(1 to 4)
        val composed = rechunk(10) >>> rechunk(2)
        for {
          chunks <- composed(base).chunks.runCollect
        } yield {
          val allChunks = chunks.toList
          val flattened = chunks.foldLeft(Chunk.empty[Int])(_ ++ _)
          assert(flattened)(equalTo(Chunk.fromIterable(1 to 4))) &&
          assert(allChunks.forall(_.size <= 2))(isTrue)
        }
      }
    )
  )
}
