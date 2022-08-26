package zio.stream

import zio._
import zio.test.Assertion.{equalTo, fails}
import zio.test._

import scala.io.Source

object ZPipelineSpec extends ZIOBaseSpec {
  def spec =
    suite("ZPipelineSpec")(
      suite("groupAdjacentBy")(
        test("groups elements across chunks") {
          ZStream
            .fromChunks(Chunk(10, 20), Chunk(30, 11, 21), Chunk(19, 42, 62, 13), Chunk(54, 32), Chunk(92))
            .via(ZPipeline.groupAdjacentBy(i => i.toString.last))
            .map(_._2)
            .runCollect
            .map(res =>
              assert(res)(
                equalTo(
                  Chunk(
                    NonEmptyChunk(10, 20, 30),
                    NonEmptyChunk(11, 21),
                    NonEmptyChunk(19),
                    NonEmptyChunk(42, 62),
                    NonEmptyChunk(13),
                    NonEmptyChunk(54),
                    NonEmptyChunk(32, 92)
                  )
                )
              )
            )
        }
      ),
      suite("utf8Encode")(
        test("encode chunks") {
          ZStream
            .fromChunks(Chunk.single("1"), Chunk.single("2"))
            .via(ZPipeline.utf8Encode)
            .runCollect
            .map(res => assert(res)(equalTo(Chunk[Byte](49, 50))))
        }
      ),
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
        },
        test("issue #6360") {
          ZStream.fromChunk(Chunk("AAAAABBBB#\r\r\r\n", "test")).via(ZPipeline.splitLines).runCollect.map { res =>
            assertTrue(res == Chunk("AAAAABBBB#\r\r", "test"))
          }
        }
      ),
      suite("mapChunksZIO")(
        test("maps chunks with effect") {
          val pipeline = ZPipeline.mapChunksZIO[Any, Nothing, Int, String] { chunk =>
            ZIO.succeed(chunk.map(_.toString.reverse))
          }
          assertZIO(
            pipeline(ZStream(12, 23, 34)).runCollect
          )(equalTo(Chunk("21", "32", "43")))
        }
      ),
      suite("splitOn")(
        test("preserves data")(check(Gen.chunkOf(Gen.string.filter(!_.contains("|")).filter(_.nonEmpty))) { lines =>
          val data     = lines.mkString("|")
          val pipeline = ZPipeline.splitOn("|")
          assertZIO(pipeline(ZStream.fromChunks(Chunk.single(data))).runCollect)(equalTo(lines))
        }),
        test("handles leftovers") {
          val pipeline = ZPipeline.splitOn("\n")
          assertZIO(pipeline(ZStream.fromChunks(Chunk("ab", "c\nb"), Chunk("c"))).runCollect)(
            equalTo(Chunk("abc", "bc"))
          )
        },
        test("works") {
          assertZIO(
            ZStream("abc", "delimiter", "bc", "delimiter", "bcd", "bcd")
              .via(ZPipeline.splitOn("delimiter"))
              .runCollect
          )(equalTo(Chunk("abc", "bc", "bcdbcd")))
        },
        test("single newline edgecase") {
          assertZIO(
            ZStream("test").via(ZPipeline.splitOn("test")).runCollect
          )(equalTo(Chunk("")))
        },
        test("no delimiter in data") {
          assertZIO(
            ZStream("abc", "abc", "abc").via(ZPipeline.splitOn("hello")).runCollect
          )(equalTo(Chunk("abcabcabc")))
        },
        test("delimiter on the boundary") {
          assertZIO(
            ZStream("abc<", ">abc").via(ZPipeline.splitOn("<>")).runCollect
          )(equalTo(Chunk("abc", "abc")))
        }
      ),
      suite("take")(
        test("it takes the correct number of elements") {
          assertZIO(
            ZStream(1, 2, 3, 4, 5).via(ZPipeline.take(3)).runCollect
          )(equalTo(Chunk(1, 2, 3)))
        },
        test("it takes all elements if n is larger than the ZStream") {
          assertZIO(
            ZStream(1, 2, 3, 4, 5).via(ZPipeline.take(100)).runCollect
          )(equalTo(Chunk(1, 2, 3, 4, 5)))
        }
      ),
      test("mapError")(
        assertZIO(
          ZStream(1, 2, 3)
            .via(
              ZPipeline
                .fromChannel(ZChannel.fail("failed"))
                .mapError(_ + "!!!")
            )
            .runCollect
            .exit
        )(fails(equalTo("failed!!!")))
      )
    )

  val weirdStringGenForSplitLines: Gen[Any, Chunk[String]] = Gen
    .chunkOf(Gen.string(Gen.printableChar).map(_.filterNot(c => c == '\n' || c == '\r')))
    .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)

  def testSplitLines(input: Seq[Chunk[String]]): ZIO[Any, Nothing, TestResult] = {
    val str      = input.flatMap(_.mkString).mkString
    val expected = Chunk.fromIterable(Source.fromString(str).getLines().toList)
    ZStream.fromChunks(input: _*).via(ZPipeline.splitLines).runCollect.map { res =>
      assertTrue(res == expected)
    }
  }
}
