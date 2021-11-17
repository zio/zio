package zio.stream

import zio._
import zio.test.Assertion.equalTo
import zio.test._

import scala.io.Source

object ZPipelineSpec extends ZIOBaseSpec {

  def spec =
    suite("ZPipelineSpec")(
      suite("type composition")(
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
      ),
      suite("splitLines")(
        test("preserves data")(
          check(weirdStringGenForSplitLines) { lines =>
            val data = lines.mkString("\n")

            ZPipeline
              .splitLines(ZStream.fromChunk(Chunk(data)))
              .runCollect
              .map(res => assert(res.mkString("\n"))(equalTo(data)))
          }
        ),
        test("preserves data in chunks") {
          check(weirdStringGenForSplitLines) { xs =>
            val data = Chunk.fromIterable(xs.sliding(2, 2).toList.map(_.mkString("\n")))
            testSplitLines(Seq(data))
          }
        },
        test("handles leftovers") {
          testSplitLines(Seq(Chunk("abc\nbc")))
        },
        test("handles leftovers 2") {
          testSplitLines(Seq(Chunk("aa", "bb"), Chunk("\nbbc\n", "ddb", "bd"), Chunk("abc", "\n"), Chunk("abc")))
        },
        test("aggregates chunks") {
          testSplitLines(Seq(Chunk("abc", "\n", "bc", "\n", "bcd", "bcd")))
        },
        test("single newline edgecase") {
          testSplitLines(Seq(Chunk("\n")))
        },
        test("no newlines in data") {
          testSplitLines(Seq(Chunk("abc", "abc", "abc")))
        },
        test("\\r\\n on the boundary") {
          testSplitLines(Seq(Chunk("abc\r", "\nabc")))
        }
      ),
      suite("splitOn")(
        test("preserves data")(check(Gen.chunkOf(Gen.string.filter(!_.contains("|")).filter(_.nonEmpty))) { lines =>
          val data     = lines.mkString("|")
          val pipeline = ZPipeline.splitOn("|")
          assertM(pipeline(ZStream.fromChunks(Chunk.single(data))).runCollect)(equalTo(lines))
        }),
        test("handles leftovers") {
          val pipeline = ZPipeline.splitOn("\n")
          assertM(pipeline(ZStream.fromChunks(Chunk("ab", "c\nb"), Chunk("c"))).runCollect)(equalTo(Chunk("abc", "bc")))
        },
        test("works") {
          assertM(
            ZPipeline
              .splitOn("delimiter")(
                ZStream("abc", "delimiter", "bc", "delimiter", "bcd", "bcd")
              )
              .runCollect
          )(equalTo(Chunk("abc", "bc", "bcdbcd")))
        },
        test("single newline edgecase") {
          assertM(
            ZPipeline.splitOn("test")(ZStream("test")).runCollect
          )(equalTo(Chunk("")))
        },
        test("no delimiter in data") {
          assertM(
            ZPipeline.splitOn("hello")(ZStream("abc", "abc", "abc")).runCollect
          )(equalTo(Chunk("abcabcabc")))
        },
        test("delimiter on the boundary") {
          assertM(
            ZPipeline.splitOn("<>")(ZStream("abc<", ">abc")).runCollect
          )(equalTo(Chunk("abc", "abc")))
        }
      ),
      suite("take")(
        test("it takes the correct number of elements") {
          assertM(
            ZPipeline.take(3)(ZStream(1, 2, 3, 4, 5)).runCollect
          )(equalTo(Chunk(1, 2, 3)))
        },
        test("it takes all elements if n is larger than the ZStream") {
          assertM(
            ZPipeline.take(100)(ZStream(1, 2, 3, 4, 5)).runCollect
          )(equalTo(Chunk(1, 2, 3, 4, 5)))
        }
      )
    )

  val weirdStringGenForSplitLines: Gen[Has[Random] with Has[Sized], Chunk[String]] = Gen
    .chunkOf(Gen.string(Gen.printableChar).map(_.filterNot(c => c == '\n' || c == '\r')))
    .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)

  def testSplitLines(input: Seq[Chunk[String]]): ZIO[Any, Nothing, TestResult] = {
    val str      = input.flatMap(_.mkString).mkString
    val expected = Chunk.fromIterable(Source.fromString(str).getLines().toList)
    ZPipeline.splitLines(ZStream.fromChunks(input: _*)).runCollect.map { res =>
      assert(res)(equalTo(expected))
    }
  }
}
