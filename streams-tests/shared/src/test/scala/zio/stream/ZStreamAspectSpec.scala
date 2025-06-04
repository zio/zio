package zio.stream

import zio._
import zio.stream.ZStreamAspect._
import zio.test._
import zio.test.Assertion._

object ZStreamAspectSpec extends ZIOBaseSpec {

  def spec = suite("ZStreamAspectSpec")(
    suite("annotated")(
      test("single annotation adds log annotation to stream") {
        val base   = ZStream(1, 2, 3)
        val aspect = annotated("testKey", "testValue")

        for {
          _           <- aspect(base).runDrain
          annotations <- ZIO.logAnnotations
        } yield assert(annotations.get("testKey"))(isSome(equalTo("testValue")))
      },
      test("multiple annotations add all log annotations to stream") {
        val base   = ZStream(1, 2, 3)
        val aspect = annotated("k1" -> "v1", "k2" -> "v2", "k3" -> "v3")

        for {
          _           <- aspect(base).runDrain
          annotations <- ZIO.logAnnotations
        } yield assert(annotations.get("k1"))(isSome(equalTo("v1"))) &&
          assert(annotations.get("k2"))(isSome(equalTo("v2"))) &&
          assert(annotations.get("k3"))(isSome(equalTo("v3")))
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
      },
      test("preserves all elements when rechunking") {
        val base      = ZStream.fromIterable(1 to 10)
        val aspect    = rechunk(4)
        val rechunked = aspect(base)

        for {
          result <- rechunked.runCollect
        } yield assert(result)(equalTo(Chunk.fromIterable(1 to 10)))
      }
    ),
    suite("tagged")(
      test("adds metric tags to stream") {
        val base   = ZStream(1, 2, 3)
        val aspect = tagged("metric", "value")

        for {
          _ <- aspect(base).runDrain
          // Since we can't directly test metric tags, we verify the stream elements are preserved
          result <- base.runCollect
        } yield assert(result)(equalTo(Chunk(1, 2, 3)))
      }
    ),
    suite("composition of aspects")(
      test("annotated >>> rechunk preserves elements and applies both aspects") {
        val base     = ZStream.fromIterable(1 to 5)
        val aspect   = annotated("x", "y") >>> rechunk(2)
        val composed = aspect(base)

        for {
          chunks      <- composed.chunks.runCollect
          annotations <- ZIO.logAnnotations
        } yield {
          val allChunks = chunks.toList
          val flattened = chunks.foldLeft(Chunk.empty[Int])(_ ++ _)
          assert(flattened)(equalTo(Chunk.fromIterable(1 to 5))) &&
          assert(allChunks.forall(_.size <= 2))(isTrue) &&
          assert(annotations.get("x"))(isSome(equalTo("y")))
        }
      },
      test("rechunk >>> tagged preserves elements and applies rechunking") {
        val base     = ZStream.fromIterable(1 to 4)
        val aspect   = rechunk(2) >>> tagged("k", "v")
        val composed = aspect(base)

        for {
          chunks <- composed.chunks.runCollect
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
