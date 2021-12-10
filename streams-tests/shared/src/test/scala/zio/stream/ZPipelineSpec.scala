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
      suite("mapChunksZIO")(
        test("maps chunks with effect") {
          val pipeline = ZPipeline.mapChunksZIO { (chunk: Chunk[Int]) =>
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

  val weirdStringGenForSplitLines: Gen[Random with Sized, Chunk[String]] = Gen
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
