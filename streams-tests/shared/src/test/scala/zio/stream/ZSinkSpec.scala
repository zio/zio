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
            .run(ZSink.collectAllToSet[Int])
        )(equalTo(Set(1, 2, 3, 4)))
      ),
      testM("collectAllToMap")(
        assertM(
          ZStream
            .range(0, 10)
            .run(ZSink.collectAllToMap((_: Int) % 3)(_ + _))
        )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
      ),
      /*suite("collectAllWhileWith")(
        testM("example 1") {
          ZIO
            .foreach(List(1, 3, 20)) { chunkSize =>
              assertM(
                Stream
                  .fromIterable(1 to 10)
                  .chunkN(chunkSize)
                  .run(ZSink.sum[Int].collectAllWhileWith(-1)((s: Int) => s == s)(_ + _))
              )(equalTo(54))
            }
            .map(_.reduce(_ && _))
        },
        testM("example 2") {
          val sink: ZSink[Any, Nothing, Int, Int, List[Int]] = ZSink
            .head[Int]
            .collectAllWhileWith[List[Int]](Nil)((a: Option[Int]) => a.fold(true)(_ < 5))(
              (a: List[Int], b: Option[Int]) => a ++ b
            )
          val stream = Stream.fromIterable(1 to 100)
          assertM((stream ++ stream).chunkN(3).run(sink))(equalTo(List(1, 2, 3, 4)))
        }
      ),*/
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
        assertM(ZStream.range(1, 10).run(ZSink.fail[String, Int]("fail").mapError(s => s + "!")).either)(
          equalTo(Left("fail!"))
        )
      ),
      suite("fold")(
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= 5)((a, b) => a + b)))(equalTo(6))
        ),
        testM("immediate termination")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= -1)((a, b) => a + b)))(equalTo(0))
        ),
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= 500)((a, b) => a + b)))(equalTo(45))
        )
      ),
      suite("fail")(
        testM("handles leftovers") {
          val s: ZSink[Any, Nothing, Int, Nothing, (Chunk[Int], String)] =
            ZSink
              .fail[String, Int]("boom")
              .foldM(err => ZSink.collectAll.map(c => (c, err)), _ => sys.error("impossible"))
          assertM(ZStream(1, 2, 3).run(s))(equalTo((Chunk(1, 2, 3), "boom")))
        }
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
      ),
      suite("foreach")(
        testM("preserves leftovers in case of failure") {
          for {
            acc <- Ref.make[Int](0)
            s   = ZSink.foreach[Any, String, Int]((i: Int) => if (i == 4) ZIO.fail("boom") else acc.update(_ + i))
            sink: ZSink[Any, Nothing, Int, Nothing, Chunk[Int]] = s
              .foldM(_ => ZSink.collectAll, _ => sys.error("impossible"))
            leftover <- ZStream.fromChunks(Chunk(1, 2), Chunk(3, 4, 5)).run(sink)
            sum      <- acc.get
          } yield {
            assert(sum)(equalTo(6)) && assert(leftover)(equalTo(Chunk(5)))
          }
        }
      ),
      suite("fromEffect")(
        testM("handles leftovers (happy)") {
          val s = ZSink.fromEffect[Any, Nothing, Int, String](ZIO.succeed("ok"))
          assertM(ZStream(1, 2, 3).run(s.exposeLeftover))(
            equalTo(("ok", Chunk(1, 2, 3)))
          )
        }
      ),
      suite("succeed")(
        testM("handles leftovers") {
          assertM(ZStream(1, 2, 3).run(ZSink.succeed[Int, String]("ok").exposeLeftover))(
            equalTo(("ok", Chunk(1, 2, 3)))
          )
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
            assertM(ZStream(1, 2, 3).run(ZSink.head.zipParRight(ZSink.succeed[Int, String]("Hello"))))(
              equalTo(("Hello"))
            )
          }
        ),
        suite("zipWith")(testM("happy path") {
          assertM(ZStream(1, 2, 3).run(ZSink.head.zipParLeft(ZSink.succeed[Int, String]("Hello"))))(
            equalTo(Some(1))
          )
        })
      ),
      testM("untilOutputM") {
        val sink: ZSink[Any, Nothing, Int, Int, Option[Option[Int]]] =
          ZSink.head[Int].untilOutputM(h => ZIO.succeed(h.fold(false)(_ >= 10)))
        val assertions = ZIO.foreach(Chunk(1, 3, 7, 20)) { n =>
          assertM(Stream.fromIterable(1 to 100).chunkN(n).run(sink))(equalTo(Some(Some(10))))
        }
        assertions.map(tst => tst.reduce(_ && _))
      },
      suite("flatMap")(
        testM("non-empty input") {
          assertM(
            ZStream(1, 2, 3).run(ZSink.head[Int].flatMap((x: Option[Int]) => ZSink.succeed[Int, Option[Int]](x)))
          )(equalTo(Some(1)))
        },
        testM("empty input") {
          assertM(ZStream.empty.run(ZSink.head[Int].flatMap(h => ZSink.succeed[Int, Option[Int]](h))))(
            equalTo(None)
          )
        },
        testM("with leftovers") {
          val headAndCount: ZSink[Any, Nothing, Int, Int, (Option[Int], Long)] =
            ZSink.head[Int].flatMap(h => ZSink.count.map(cnt => (h, cnt)))
          checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
            ZStream.fromChunks(chunks: _*).run(headAndCount).map {
              case (head, count) => {
                assert(head)(equalTo(chunks.flatten.headOption)) &&
                assert(count + head.size)(equalTo(chunks.map(_.size.toLong).sum))
              }
            }
          }
        }
      )
    )
  )
}
