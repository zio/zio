package zio.stream

import scala.util.Random

import zio.ZIOBaseSpec
import zio._
import zio.stream.SinkUtils.{ findSink, sinkRaceLaw }
import zio.stream.ZStreamGen._
import zio.test.Assertion.{ equalTo, isTrue, succeeds }
import zio.test.{ assertM, _ }

object ZSinkSpec extends ZIOBaseSpec {
  def spec = suite("ZSinkSpec")(
    suite("Constructors")(
      testM("collectAllToSet")(
        assertM(
          ZStream(1, 2, 3, 3, 4)
            .run(ZSink.collectAllToSet)
        )(equalTo(Set(1, 2, 3, 4)))
      ),
      testM("collectAllToMap")(
        assertM(
          ZStream
            .range(0, 10)
            .run(ZSink.collectAllToMap((_: Int) % 3)(_ + _))
        )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
      ),
      suite("collectAllWhileWith")(
        testM("example 1") {
          ZIO
            .foreach(List(1, 3, 20)) { chunkSize =>
              assertM(
                Stream
                  .fromIterable(1 to 10)
                  .chunkN(chunkSize)
                  .flattenChunks
                  .run(ZSink.sum[Int].collectAllWhileWith(-1)((s: Int) => s == s)(_ + _))
              )(equalTo(54))
            }
            .map(_.reduce(_ && _))
        },
        testM("example 2") {
          val sink: ZSink[Any, Nothing, Int, List[Int]] = ZSink
            .head[Int]
            .collectAllWhileWith[List[Int]](Nil)((a: Option[Int]) => a.fold(true)(_ < 15))(
              (a: List[Int], b: Option[Int]) => a ++ b
            )
          val stream = Stream.fromChunks(1 to 100 map Chunk.single: _*)
          assertM((stream ++ stream).chunkN(3).flattenChunks.run(sink))(equalTo(List(1, 4, 7, 10, 13)))
        }
      ),
      testM("head")(
        checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
          val headOpt = ZStream.fromChunks(chunks: _*).run(ZSink.head[Int])
          assertM(headOpt)(equalTo(chunks.flatMap(_.toSeq).headOption))
        }
      ),
      testM("last")(
        checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
          val lastOpt = ZStream.fromChunks(chunks: _*).run(ZSink.last[Int])
          assertM(lastOpt)(equalTo(chunks.flatMap(_.toSeq).lastOption))
        }
      ),
      testM("foldLeft")(
        checkM(
          Gen.small(pureStreamGen(Gen.anyInt, _)),
          Gen.function2(Gen.anyString),
          Gen.anyString
        ) { (s, f, z) =>
          for {
            xs <- s.run(ZSink.foldLeft(z)(f))
            ys <- s.runCollect.map(_.foldLeft(z)(f))
          } yield assert(xs)(equalTo(ys))
        }
      ),
      testM("mapError")(
        assertM(ZStream.range(1, 10).run(ZSink.fail("fail").mapError(s => s + "!")).either)(equalTo(Left("fail!")))
      ),
      suite("fold")(
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold(0)(_ <= 5)((a, b) => a + b)))(equalTo(6))
        ),
        testM("immediate termination")(
          assertM(ZStream.range(1, 10).run(ZSink.fold(0)(_ <= -1)((a, b) => a + b)))(equalTo(0))
        ),
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold(0)(_ <= 500)((a, b) => a + b)))(equalTo(45))
        )
      ),
      suite("foldM")(
        testM("foldM") {
          val ioGen = successes(Gen.anyString)
          checkM(Gen.small(pureStreamGen(Gen.anyInt, _)), Gen.function2(ioGen), ioGen) { (s, f, z) =>
            for {
              sinkResult <- z.flatMap(z => s.run(ZSink.foldLeftM(z)(f)))
              foldResult <- s.fold(List[Int]())((acc, el) => el :: acc)
                             .map(_.reverse)
                             .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
                             .run
            } yield assert(foldResult.succeeded)(isTrue) implies assert(foldResult)(succeeds(equalTo(sinkResult)))
          }
        }
      )
    ),
    suite("Combinators")(
      testM("raceBoth") {
        checkM(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
          val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
          sinkRaceLaw(ZStream.fromIterable(Random.shuffle(stream)), findSink(20), findSink(40))
        }
      },
      suite("zipWithPar")(
        testM("coherence") {
          checkM(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
            val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
            SinkUtils
              .zipParLaw(ZStream.fromIterable(Random.shuffle(stream)), findSink(20), findSink(40))
          }
        },
        suite("zipRight (*>)")(
          testM("happy path") {
            assertM(ZStream(1, 2, 3).run(ZSink.head.zipParRight(ZSink.succeed("Hello"))))(equalTo(("Hello")))
          }
        ),
        suite("zipWith")(testM("happy path") {
          assertM(ZStream(1, 2, 3).run(ZSink.head.zipParLeft(ZSink.succeed("Hello"))))(equalTo(Some(1)))
        })
      ),
      suite("flatMap")(
        testM("non-empty input") {
          assertM(ZStream(1, 2, 3).run(ZSink.head[Int].flatMap(ZSink.succeed(_))))(equalTo(Some(1)))
        },
        testM("empty input") {
          assertM(ZStream.empty.run(ZSink.head[Int].flatMap(ZSink.succeed(_))))(equalTo(None))
        }
      )
    )
  )
}
