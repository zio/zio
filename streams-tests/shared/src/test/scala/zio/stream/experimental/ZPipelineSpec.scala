package zio.stream.experimental

import zio._
import zio.test._

object ZPipelineSpec extends ZIOBaseSpec {

  def spec = suite("ZPipelineSpec")(
    test("pipelines are polymorphic") {
      val pipeline = ZPipeline.identity
      val stream1  = ZStream(1, 2, 3)
      val stream2  = ZStream("foo", "bar", "baz")
      for {
        result1 <- pipeline(stream1).runCollect
        result2 <- pipeline(stream2).runCollect
      } yield assertTrue(result1 == Chunk(1, 2, 3)) &&
        assertTrue(result2 == Chunk("foo", "bar", "baz"))
    },
    test("polymorphic pipelines can be composed") {
      val pipeline1 = ZPipeline.identity
      val pipeline2 = ZPipeline.identity
      val pipeline3 = pipeline1 >>> pipeline2
      val stream    = ZStream(1, 2, 3)
      for {
        result <- pipeline3(stream).runCollect
      } yield assertTrue(result == Chunk(1, 2, 3))
    },
    test("monomorphic pipelines can be composed") {
      val pipeline1 = ZPipeline.map[String, Double](_.toDouble)
      val pipeline2 = ZPipeline.map[Double, Int](_.toInt)
      val pipeline3 = pipeline1 >>> pipeline2
      val stream    = ZStream("1", "2", "3")
      for {
        result <- pipeline3(stream).runCollect
      } yield assertTrue(result == Chunk(1, 2, 3))
    },
    test("monomorphic and polymorphic pipelines can be composed") {
      val pipeline1 = ZPipeline.map[String, Double](_.toDouble)
      val pipeline2 = ZPipeline.map[Double, Int](_.toInt)
      val pipeline3 = pipeline1 >>> pipeline2
      val pipeline4 = ZPipeline.identity
      val pipeline5 = pipeline3 >>> pipeline4
      val stream    = ZStream("1", "2", "3")
      for {
        result <- pipeline5(stream).runCollect
      } yield assertTrue(result == Chunk(1, 2, 3))
    },
    test("polymorphic and monomorphic pipelines can be composed") {
      val pipeline1 = ZPipeline.map[String, Double](_.toDouble)
      val pipeline2 = ZPipeline.map[Double, Int](_.toInt)
      val pipeline3 = pipeline1 >>> pipeline2
      val pipeline4 = ZPipeline.identity
      val pipeline5 = pipeline4 >>> pipeline3
      val stream    = ZStream("1", "2", "3")
      for {
        result <- pipeline5(stream).runCollect
      } yield assertTrue(result == Chunk(1, 2, 3))
    },
    test("pipelines can provide the environment") {
      val pipeline = ZPipeline.provide(42)
      val stream   = ZStream.environment[Int]
      for {
        result <- pipeline(stream).runCollect
      } yield assertTrue(result == Chunk(42))
    }
  )
}
