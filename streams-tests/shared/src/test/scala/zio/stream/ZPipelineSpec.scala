package zio.stream

import zio._
import zio.test.Assertion.equalTo
import zio.test._

import scala.io.Source

object ZPipelineSpec extends ZIOBaseSpec {

  def spec =
    suite("ZPipelineSpec")(
      suite("splitLines")(
        test("preserves data")(
          check(weirdStringGenForSplitLines) { lines =>
            val data = lines.mkString("\n")

            ZStream
              .fromChunk(Chunk(data))
              .via(ZPipeline.splitLines)
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
      suite("mapChunksZIO")(
        test("maps chunks with effect") {
          val pipeline = ZPipeline.mapChunksZIO[Any, Nothing, Int, String] { chunk =>
            ZIO.succeed(chunk.map(_.toString.reverse))
          }
          assertM(
            pipeline(ZStream(12, 23, 34)).runCollect
          )(equalTo(Chunk("21", "32", "43")))
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
            ZStream("abc", "delimiter", "bc", "delimiter", "bcd", "bcd")
              .via(ZPipeline.splitOn("delimiter"))
              .runCollect
          )(equalTo(Chunk("abc", "bc", "bcdbcd")))
        },
        test("single newline edgecase") {
          assertM(
            ZStream("test").via(ZPipeline.splitOn("test")).runCollect
          )(equalTo(Chunk("")))
        },
        test("no delimiter in data") {
          assertM(
            ZStream("abc", "abc", "abc").via(ZPipeline.splitOn("hello")).runCollect
          )(equalTo(Chunk("abcabcabc")))
        },
        test("delimiter on the boundary") {
          assertM(
            ZStream("abc<", ">abc").via(ZPipeline.splitOn("<>")).runCollect
          )(equalTo(Chunk("abc", "abc")))
        }
      ),
      suite("take")(
        test("it takes the correct number of elements") {
          assertM(
            ZStream(1, 2, 3, 4, 5).via(ZPipeline.take(3)).runCollect
          )(equalTo(Chunk(1, 2, 3)))
        },
        test("it takes all elements if n is larger than the ZStream") {
          assertM(
            ZStream(1, 2, 3, 4, 5).via(ZPipeline.take(100)).runCollect
          )(equalTo(Chunk(1, 2, 3, 4, 5)))
        }
      ),
      suite("fromSinkAndSplit")(
        test("should split a stream on predicate and run each part into the sink") {
          val in = ZStream(1, 2, 3, 4, 5, 6, 7, 8)
          for {
            res <- in.via(ZPipeline.fromSinkAndSplit(ZSink.collectAll[Int])((i: Int) => i % 2 == 0)).runCollect
          } yield {
            assertTrue(res == Chunk(Chunk(1), Chunk(2, 3), Chunk(4, 5), Chunk(6, 7), Chunk(8)))
          }
        },
        test("should split a stream on predicate and run each part into the sink, in several chunks") {
          val in = ZStream.fromChunks(Chunk(1, 2, 3, 4), Chunk(5, 6, 7, 8))
          for {
            res <- in.via(ZPipeline.fromSinkAndSplit(ZSink.collectAll[Int])((i: Int) => i % 2 == 0)).runCollect
          } yield {
            assertTrue(res == Chunk(Chunk(1), Chunk(2, 3), Chunk(4, 5), Chunk(6, 7), Chunk(8)))
          }
        },
        test("not yield an empty sink if split on the first element") {
          val in = ZStream(1, 2, 3, 4, 5, 6, 7, 8)
          for {
            res <- in.via(ZPipeline.fromSinkAndSplit(ZSink.collectAll[Int])((i: Int) => i % 2 != 0)).runCollect
          } yield {
            assertTrue(res == Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6), Chunk(7, 8)))
          }
        }
      )
    )

  val weirdStringGenForSplitLines: Gen[Random with Sized, Chunk[String]] = Gen
    .chunkOf(Gen.string(Gen.printableChar).map(_.filterNot(c => c == '\n' || c == '\r')))
    .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)

  def testSplitLines(input: Seq[Chunk[String]]): ZIO[Any, Nothing, TestResult] = {
    val str      = input.flatMap(_.mkString).mkString
    val expected = Chunk.fromIterable(Source.fromString(str).getLines().toList)
    ZStream.fromChunks(input: _*).via(ZPipeline.splitLines).runCollect.map { res =>
      assert(res)(equalTo(expected))
    }
  }
}
