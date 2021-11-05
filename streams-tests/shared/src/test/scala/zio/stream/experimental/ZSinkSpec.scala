package zio.stream.experimental

import zio._
import zio.stream.internal.CharacterSet._
import zio.stream.experimental.SinkUtils._
import zio.test.Assertion._
import zio.test.TestAspect.jvmOnly
import zio.test._

import java.nio.charset.{Charset, StandardCharsets}

object ZSinkSpec extends ZIOBaseSpec {

  import ZIOTag._

  private def stringToByteChunkOf(charset: Charset, source: String): Chunk[Byte] =
    Chunk.fromArray(source.getBytes(charset))

  private def testDecoderWithRandomStringUsing[Err](
    decoderUnderTest: => ZSink[Any, Err, Byte, Err, Byte, Option[String]],
    sourceCharset: Charset,
    withBom: Chunk[Byte] => Chunk[Byte] = identity
  ) =
    check(
      Gen.string.map(stringToByteChunkOf(sourceCharset, _)),
      Gen.int
    ) {
      // Enabling `rechunk(chunkSize)` makes this suite run for too long and
      // could potentially cause OOM during builds. However, running the tests with
      // `rechunk(chunkSize)` can guarantee that different chunks have no impact on
      // the functionality of decoders. You should run it at least once locally before
      // pushing your commit.
      (originalBytes, _ /*chunkSize*/ ) =>
        ZStream
          .fromChunk(withBom(originalBytes))
//        .rechunk(chunkSize)
          .transduce(decoderUnderTest.map(_.getOrElse("")))
          .mkString
          .map { decodedString =>
            assertTrue(originalBytes == stringToByteChunkOf(sourceCharset, decodedString))
          }
    }

  private def runOnlyIfSupporting(charset: String) =
    if (Charset.isSupported(charset)) TestAspect.jvmOnly
    else TestAspect.ignore

  def spec: ZSpec[Environment, Failure] = {
    suite("ZSinkSpec")(
      suite("Constructors")(
        suite("collectAllN")(
          test("respects the given limit") {
            ZStream
              .fromChunk(Chunk(1, 2, 3, 4))
              .transduce(ZSink.collectAllN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Chunk(1, 2, 3), Chunk(4)))))
          },
          test("produces empty trailing chunks") {
            ZStream
              .fromChunk(Chunk(1, 2, 3, 4))
              .transduce(ZSink.collectAllN[Nothing, Int](4))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Chunk(1, 2, 3, 4), Chunk()))))
          },
          test("handles empty input") {
            ZStream
              .fromChunk(Chunk.empty: Chunk[Int])
              .transduce(ZSink.collectAllN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Chunk()))))
          }
        ),
        test("collectAllToSet")(
          assertM(
            ZStream(1, 2, 3, 3, 4)
              .run(ZSink.collectAllToSet[Nothing, Int])
          )(equalTo(Set(1, 2, 3, 4)))
        ),
        suite("collectAllToSetN")(
          test("respect the given limit") {
            ZStream
              .fromChunks(Chunk(1, 2, 1), Chunk(2, 3, 3, 4))
              .transduce(ZSink.collectAllToSetN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Set(1, 2, 3), Set(4)))))
          },
          test("handles empty input") {
            ZStream
              .fromChunk(Chunk.empty: Chunk[Int])
              .transduce(ZSink.collectAllToSetN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Set.empty[Int]))))
          }
        ),
        test("collectAllToMap")(
          assertM(
            ZStream
              .range(0, 10)
              .run(ZSink.collectAllToMap((_: Int) % 3)(_ + _))
          )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
        ),
        suite("collectAllToMapN")(
          test("respects the given limit") {
            ZStream
              .fromChunk(Chunk(1, 1, 2, 2, 3, 2, 4, 5))
              .transduce(ZSink.collectAllToMapN(2)((_: Int) % 3)(_ + _))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Map(1 -> 2, 2 -> 4), Map(0 -> 3, 2 -> 2), Map(1 -> 4, 2 -> 5)))))
          },
          test("collects as long as map size doesn't exceed the limit") {
            ZStream
              .fromChunks(Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6, 7, 8, 9))
              .transduce(ZSink.collectAllToMapN(3)((_: Int) % 3)(_ + _))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Map(0 -> 18, 1 -> 12, 2 -> 15)))))
          },
          test("handles empty input") {
            ZStream
              .fromChunk(Chunk.empty: Chunk[Int])
              .transduce(ZSink.collectAllToMapN(2)((_: Int) % 3)(_ + _))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Map.empty[Int, Int]))))
          }
        ),
        test("dropWhile")(
          assertM(
            ZStream(1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
              .pipeThrough(ZSink.dropWhile[Nothing, Int](_ < 3))
              .runCollect
          )(equalTo(Chunk(3, 4, 5, 1, 2, 3, 4, 5)))
        ),
        suite("dropWhileZIO")(
          test("happy path")(
            assertM(
              ZStream(1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
                .dropWhileZIO(x => UIO(x < 3))
                .runCollect
            )(equalTo(Chunk(3, 4, 5, 1, 2, 3, 4, 5)))
          ),
          test("error")(
            assertM {
              (ZStream(1, 2, 3) ++ ZStream.fail("Aie") ++ ZStream(5, 1, 2, 3, 4, 5))
                .pipeThrough(ZSink.dropWhileZIO[Any, String, Int](x => UIO(x < 3)))
                .either
                .runCollect
            }(equalTo(Chunk(Right(3), Left("Aie"))))
          )
        ),
        suite("accessSink")(
          test("accessSink") {
            assertM(
              ZStream("ignore this")
                .run(ZSink.accessSink[String](ZSink.succeed(_)).provide("use this"))
            )(equalTo("use this"))
          }
        ),
        suite("collectAllWhileWith")(
          test("example 1") {
            ZIO
              .foreach(List(1, 3, 20)) { chunkSize =>
                assertM(
                  ZStream
                    .fromChunk(Chunk.fromIterable(1 to 10))
                    .rechunk(chunkSize)
                    .run(ZSink.sum[Nothing, Int].collectAllWhileWith(-1)((s: Int) => s == s)(_ + _))
                )(equalTo(54))
              }
              .map(_.reduce(_ && _))
          },
          test("example 2") {
            val sink = ZSink
              .head[Nothing, Int]
              .collectAllWhileWith[List[Int]](Nil)((a: Option[Int]) => a.fold(true)(_ < 5))(
                (a: List[Int], b: Option[Int]) => a ++ b.toList
              )
            val stream = ZStream.fromChunk(Chunk.fromIterable(1 to 100))
            assertM((stream ++ stream).rechunk(3).run(sink))(equalTo(List(1, 2, 3, 4)))
          }
        ),
        test("head")(
          check(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.int)))) { chunks =>
            val headOpt = ZStream.fromChunks(chunks: _*).run(ZSink.head[Nothing, Int])
            assertM(headOpt)(equalTo(chunks.flatMap(_.toSeq).headOption))
          }
        ),
        test("last")(
          check(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.int)))) { chunks =>
            val lastOpt = ZStream.fromChunks(chunks: _*).run(ZSink.last)
            assertM(lastOpt)(equalTo(chunks.flatMap(_.toSeq).lastOption))
          }
        ),
        suite("unwrapManaged")(
          test("happy path") {
            for {
              closed <- Ref.make[Boolean](false)
              res     = ZManaged.acquireReleaseWith(ZIO.succeed(100))(_ => closed.set(true))
              sink =
                ZSink.unwrapManaged(res.map(m => ZSink.count[Any].mapZIO(cnt => closed.get.map(cl => (cnt + m, cl)))))
              resAndState <- ZStream(1, 2, 3).run(sink)
              finalState  <- closed.get
            } yield {
              assert(resAndState._1)(equalTo(103L)) && assert(resAndState._2)(isFalse) && assert(finalState)(isTrue)
            }
          },
          test("sad path") {
            for {
              closed     <- Ref.make[Boolean](false)
              res         = ZManaged.acquireReleaseWith(ZIO.succeed(100))(_ => closed.set(true))
              sink        = ZSink.unwrapManaged(res.map(_ => ZSink.succeed("ok")))
              r          <- ZStream.fail("fail").run(sink)
              finalState <- closed.get
            } yield assert(r)(equalTo("ok")) && assert(finalState)(isTrue)
          }
        )
      ),
      test("map")(
        assertM(ZStream.range(1, 10).run(ZSink.succeed(1).map(_.toString)))(
          equalTo("1")
        )
      ),
      suite("mapZIO")(
        test("happy path")(
          assertM(ZStream.range(1, 10).run(ZSink.succeed(1).mapZIO(e => ZIO.succeed(e + 1))))(
            equalTo(2)
          )
        ),
        test("failure")(
          assertM(ZStream.range(1, 10).run(ZSink.succeed(1).mapZIO(_ => ZIO.fail("fail"))).flip)(
            equalTo("fail")
          )
        )
      ),
      test("filterInput")(
        assertM(ZStream.range(1, 10).run(ZSink.collectAll.filterInput(_ % 2 == 0)))(
          equalTo(Chunk(2, 4, 6, 8))
        )
      ),
      suite("filterInputZIO")(
        test("happy path")(
          assertM(ZStream.range(1, 10).run(ZSink.collectAll.filterInputZIO(i => ZIO.succeed(i % 2 == 0))))(
            equalTo(Chunk(2, 4, 6, 8))
          )
        ),
        test("failure")(
          assertM(ZStream.range(1, 10).run(ZSink.collectAll.filterInputZIO(_ => ZIO.fail("fail"))).flip)(
            equalTo("fail")
          )
        )
      ),
      test("mapError")(
        assertM(ZStream.range(1, 10).run(ZSink.fail("fail").mapError(s => s + "!")).either)(
          equalTo(Left("fail!"))
        )
      ),
      test("as")(
        assertM(ZStream.range(1, 10).run(ZSink.succeed(1).as("as")))(
          equalTo("as")
        )
      ),
      suite("contramap")(
        test("happy path") {
          val parser = ZSink.collectAll[Nothing, Int].contramap[String](_.toInt)
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        test("error") {
          val parser = ZSink.fail("Ouch").contramap[String](_.toInt)
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramapChunks")(
        test("happy path") {
          val parser = ZSink.collectAll[Nothing, Int].contramapChunks[String](_.map(_.toInt))
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        test("error") {
          val parser = ZSink.fail("Ouch").contramapChunks[String](_.map(_.toInt))
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramapZIO")(
        test("happy path") {
          val parser = ZSink.collectAll[Throwable, Int].contramapZIO[Any, Throwable, String](a => ZIO.attempt(a.toInt))
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        test("error") {
          val parser = ZSink.fail("Ouch").contramapZIO[Any, Throwable, String](a => ZIO.attempt(a.toInt))
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        test("error in transformation") {
          val parser = ZSink.collectAll[Throwable, Int].contramapZIO[Any, Throwable, String](a => ZIO.attempt(a.toInt))
          assertM(ZStream("1", "a").run(parser).either)(isLeft(hasMessage(equalTo("For input string: \"a\""))))
        } @@ zioTag(errors)
      ),
      suite("contramapChunksZIO")(
        test("happy path") {
          val parser =
            ZSink
              .collectAll[Throwable, Int]
              .contramapChunksZIO[Any, Throwable, String](_.mapZIO(a => ZIO.attempt(a.toInt)))
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        test("error") {
          val parser =
            ZSink.fail("Ouch").contramapChunksZIO[Any, Throwable, String](_.mapZIO(a => ZIO.attempt(a.toInt)))
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        test("error in transformation") {
          val parser =
            ZSink
              .collectAll[Throwable, Int]
              .contramapChunksZIO[Any, Throwable, String](_.mapZIO(a => ZIO.attempt(a.toInt)))
          assertM(ZStream("1", "a").run(parser).either)(isLeft(hasMessage(equalTo("For input string: \"a\""))))
        } @@ zioTag(errors)
      ),
      test("collectAllWhile") {
        val sink   = ZSink.collectAllWhile[Nothing, Int](_ < 5)
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = ZStream.fromChunks(input: _*).transduce(sink).runCollect
        assertM(result)(equalTo(Chunk(Chunk(3, 4), Chunk(), Chunk(), Chunk(2, 3, 4), Chunk(), Chunk(), Chunk(4, 3, 2))))
      },
      test("collectAllWhileM") {
        val sink   = ZSink.collectAllWhileZIO[Any, Nothing, Int]((i: Int) => ZIO.succeed(i < 5))
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = ZStream.fromChunks(input: _*).transduce(sink).runCollect
        assertM(result)(equalTo(Chunk(Chunk(3, 4), Chunk(), Chunk(), Chunk(2, 3, 4), Chunk(), Chunk(), Chunk(4, 3, 2))))
      },
      test("foldLeft equivalence with Chunk#foldLeft")(
        check(
          Gen.small(ZStreamGen.pureStreamGen(Gen.int, _)),
          Gen.function2(Gen.string),
          Gen.string
        ) { (s, f, z) =>
          for {
            xs <- s.run(ZSink.foldLeft(z)(f))
            ys <- s.runCollect.map(_.foldLeft(z)(f))
          } yield assert(xs)(equalTo(ys))
        }
      ),
      suite("foldZIO")(
        test("empty")(
          assertM(
            ZStream.empty
              .transduce(
                ZSink.foldZIO(0)(_ => true)((x, y: Int) => ZIO.succeed(x + y))
              )
              .runCollect
          )(equalTo(Chunk(0)))
        ),
        test("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                        .transduce(ZSink.foldZIO(0)(_ => true) { (_, a: Int) =>
                          effects.update(a :: _) *> UIO.succeed(30)
                        })
                        .runCollect
              result <- effects.get
            } yield exit -> result).exit

          (assertM(run(empty))(succeeds(equalTo((Chunk(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((Chunk(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((Chunk(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map { case (r1, r2, r3, r4) =>
            r1 && r2 && r3 && r4
          }
        },
        test("equivalence with List#foldLeft") {
          val ioGen = ZStreamGen.successes(Gen.string)
          check(Gen.small(ZStreamGen.pureStreamGen(Gen.int, _)), Gen.function2(ioGen), ioGen) { (s, f, z) =>
            for {
              sinkResult <- z.flatMap(z => s.run(ZSink.foldLeftZIO(z)(f)))
              foldResult <- s.runFold(List[Int]())((acc, el) => el :: acc)
                              .map(_.reverse)
                              .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
                              .exit
            } yield assert(foldResult.isSuccess)(isTrue) implies assert(foldResult)(succeeds(equalTo(sinkResult)))
          }
        }
      ),
      suite("fold")(
        test("empty")(
          assertM(
            ZStream.empty
              .transduce(ZSink.fold[Nothing, Int, Int](0)(_ => true)(_ + _))
              .runCollect
          )(equalTo(Chunk(0)))
        ),
        test("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                        .transduce(ZSink.foldZIO(0)(_ => true) { (_, a: Int) =>
                          effects.update(a :: _) *> UIO.succeed(30)
                        })
                        .runCollect
              result <- effects.get
            } yield (exit, result)).exit

          (assertM(run(empty))(succeeds(equalTo((Chunk(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((Chunk(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((Chunk(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map { case (r1, r2, r3, r4) =>
            r1 && r2 && r3 && r4
          }
        },
        test("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Nothing, Int, Int](0)(_ <= 5)((a, b) => a + b)))(equalTo(6))
        ),
        test("immediate termination")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Nothing, Int, Int](0)(_ <= -1)((a, b) => a + b)))(equalTo(0))
        ),
        test("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Nothing, Int, Int](0)(_ <= 500)((a, b) => a + b)))(equalTo(45))
        )
      ),
      test("foldUntil")(
        assertM(
          ZStream[Long](1, 1, 1, 1, 1, 1)
            .transduce(ZSink.foldUntil(0L, 3)(_ + (_: Long)))
            .runCollect
        )(equalTo(Chunk(3L, 3L, 0L)))
      ),
      test("foldUntilM")(
        assertM(
          ZStream[Long](1, 1, 1, 1, 1, 1)
            .transduce(ZSink.foldUntilZIO(0L, 3)((s, a: Long) => UIO.succeedNow(s + a)))
            .runCollect
        )(equalTo(Chunk(3L, 3L, 0L)))
      ),
      test("foldWeighted")(
        assertM(
          ZStream[Long](1, 5, 2, 3)
            .transduce(
              ZSink.foldWeighted(List[Long]())((_, x: Long) => x * 2, 12)((acc, el) => el :: acc).map(_.reverse)
            )
            .runCollect
        )(equalTo(Chunk(List(1L, 5L), List(2L, 3L))))
      ),
      suite("foldWeightedDecompose")(
        test("simple example")(
          assertM(
            ZStream(1, 5, 1)
              .transduce(
                ZSink
                  .foldWeightedDecompose(List[Int]())(
                    (_, i: Int) => i.toLong,
                    4,
                    (i: Int) =>
                      if (i > 1) Chunk(i - 1, 1)
                      else Chunk(i)
                  )((acc, el) => el :: acc)
                  .map(_.reverse)
              )
              .runCollect
          )(equalTo(Chunk(List(1, 3), List(1, 1, 1))))
        ),
        test("empty stream")(
          assertM(
            ZStream.empty
              .transduce(
                ZSink.foldWeightedDecompose(0)((_, x: Int) => x.toLong, 1000, Chunk.single(_: Int))(_ + _)
              )
              .runCollect
          )(equalTo(Chunk(0)))
        )
      ),
      test("foldWeightedM")(
        assertM(
          ZStream[Long](1, 5, 2, 3)
            .transduce(
              ZSink
                .foldWeightedZIO(List.empty[Long])((_, a: Long) => UIO.succeedNow(a * 2), 12)((acc, el) =>
                  UIO.succeedNow(el :: acc)
                )
                .map(_.reverse)
            )
            .runCollect
        )(equalTo(Chunk(List(1L, 5L), List(2L, 3L))))
      ),
      suite("foldWeightedDecomposeM")(
        test("simple example")(
          assertM(
            ZStream(1, 5, 1)
              .transduce(
                ZSink
                  .foldWeightedDecomposeZIO(List.empty[Int])(
                    (_, i: Int) => UIO.succeedNow(i.toLong),
                    4,
                    (i: Int) =>
                      UIO.succeedNow(
                        if (i > 1) Chunk(i - 1, 1)
                        else Chunk(i)
                      )
                  )((acc, el) => UIO.succeedNow(el :: acc))
                  .map(_.reverse)
              )
              .runCollect
          )(equalTo(Chunk(List(1, 3), List(1, 1, 1))))
        ),
        test("empty stream")(
          assertM(
            ZStream.empty
              .transduce(
                ZSink.foldWeightedDecomposeZIO[Any, Nothing, Int, Int](0)(
                  (_, x) => ZIO.succeed(x.toLong),
                  1000,
                  x => ZIO.succeed(Chunk.single(x))
                )((x, y) => ZIO.succeed(x + y))
              )
              .runCollect
          )(equalTo(Chunk(0)))
        )
      ),
      suite("fail")(
        test("handles leftovers") {
          val s =
            ZSink
              .fail("boom")
              .foldSink(err => ZSink.collectAll[String, Int].map(c => (c, err)), _ => sys.error("impossible"))
          assertM(ZStream(1, 2, 3).run(s))(equalTo((Chunk(1, 2, 3), "boom")))
        }
      ),
      // suite("foreach")(
      //   test("preserves leftovers in case of failure") {
      //     for {
      //       acc <- Ref.make[Int](0)
      //       s    = ZSink.foreach[Any, String, Int]((i: Int) => if (i == 4) ZIO.fail("boom") else acc.update(_ + i))
      //       sink = s.foldSink(_ => ZSink.collectAll[String, Int], _ => sys.error("impossible"))
      //       leftover <- ZStream.fromChunks(Chunk(1, 2), Chunk(3, 4, 5)).run(sink)
      //       sum      <- acc.get
      //     } yield {
      //       assert(sum)(equalTo(6)) && assert(leftover)(equalTo(Chunk(5)))
      //     }
      //   }
      // ),
      suite("foreachWhile")(
        test("handles leftovers") {
          val leftover = ZStream
            .fromIterable(1 to 5)
            .run(ZSink.foreachWhile((n: Int) => ZIO.succeed(n <= 3)).exposeLeftover)
            .map(_._2)
          assertM(leftover)(equalTo(Chunk(4, 5)))
        }
      ),
      suite("fromZIO")(
        test("result is ok") {
          val s = ZSink.fromZIO(ZIO.succeed("ok"))
          assertM(ZStream(1, 2, 3).run(s))(
            equalTo("ok")
          )
        }
      ),
      suite("fromQueue")(
        test("should enqueue all elements") {

          for {
            queue          <- ZQueue.unbounded[Int]
            _              <- ZStream(1, 2, 3).run(ZSink.fromQueue(queue))
            enqueuedValues <- queue.takeAll
          } yield {
            assert(enqueuedValues)(hasSameElementsDistinct(List(1, 2, 3)))
          }

        }
      ),
      suite("fromQueueWithShutdown")(
        test("should enqueue all elements and shutsdown queue") {

          def createQueueSpy[A](q: Queue[A]) = new ZQueue[Any, Any, Nothing, Nothing, A, A] {

            @volatile
            private var isShutDown = false

            override def awaitShutdown(implicit trace: ZTraceElement): UIO[Unit] = q.awaitShutdown

            override def capacity: Int = q.capacity

            override def isShutdown(implicit trace: ZTraceElement): UIO[Boolean] = UIO(this.isShutDown)

            override def offer(a: A)(implicit trace: ZTraceElement): ZIO[Any, Nothing, Boolean] = q.offer(a)

            override def offerAll(as: Iterable[A])(implicit trace: ZTraceElement): ZIO[Any, Nothing, Boolean] =
              q.offerAll(as)

            override def shutdown(implicit trace: ZTraceElement): UIO[Unit] = UIO(this.isShutDown = true)

            override def size(implicit trace: ZTraceElement): UIO[Int] = q.size

            override def take(implicit trace: ZTraceElement): ZIO[Any, Nothing, A] = q.take

            override def takeAll(implicit trace: ZTraceElement): ZIO[Any, Nothing, Chunk[A]] = q.takeAll

            override def takeUpTo(max: Int)(implicit trace: ZTraceElement): ZIO[Any, Nothing, Chunk[A]] =
              q.takeUpTo(max)

          }

          for {
            queue          <- ZQueue.unbounded[Int].map(createQueueSpy)
            _              <- ZStream(1, 2, 3).run(ZSink.fromQueueWithShutdown(queue))
            enqueuedValues <- queue.takeAll
            isShutdown     <- queue.isShutdown
          } yield {
            assert(enqueuedValues)(hasSameElementsDistinct(List(1, 2, 3))) && assert(isShutdown)(isTrue)
          }

        }
      ),
      suite("fromHub")(
        test("should publish all elements") {

          for {
            promise1       <- Promise.make[Nothing, Unit]
            promise2       <- Promise.make[Nothing, Unit]
            hub            <- ZHub.unbounded[Int]
            f              <- hub.subscribe.use(s => promise1.succeed(()) *> promise2.await *> s.takeAll).fork
            _              <- promise1.await
            _              <- ZStream(1, 2, 3).run(ZSink.fromHub(hub))
            _              <- promise2.succeed(())
            publisedValues <- f.join
          } yield {
            assert(publisedValues)(hasSameElementsDistinct(List(1, 2, 3)))
          }

        }
      ),
      suite("fromHubWithShutdown")(
        test("should shutdown hub") {
          for {
            hub        <- ZHub.unbounded[Int]
            _          <- ZStream(1, 2, 3).run(ZSink.fromHubWithShutdown(hub))
            isShutdown <- hub.isShutdown
          } yield {
            assert(isShutdown)(isTrue)
          }

        }
      ),
      suite("succeed")(
        test("result is ok") {
          assertM(ZStream(1, 2, 3).run(ZSink.succeed("ok")))(
            equalTo("ok")
          )
        }
      ),
      suite("Combinators")(
        test("raceBoth") {
          check(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
            val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
            sinkRaceLaw(
              ZStream.fromIterableZIO(Random.shuffle(stream).provideLayer(Random.live)),
              findSink(20),
              findSink(40)
            )
          }
        },
        //      suite("zipWithPar")(
        //        test("coherence") {
        //          check(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
        //            val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
        //            SinkUtils
        //              .zipParLaw(ZStream.fromIterable(Random.shuffle(stream)), findSink(20), findSink(40))
        //          }
        //        },
        //        suite("zipRight (*>)")(
        //          test("happy path") {
        //            assertM(ZStream(1, 2, 3).run(ZSink.head.zipParRight(ZSink.succeed[Int, String]("Hello"))))(
        //              equalTo(("Hello"))
        //            )
        //          }
        //        ),
        //        suite("zipWith")(test("happy path") {
        //          assertM(ZStream(1, 2, 3).run(ZSink.head.zipParLeft(ZSink.succeed[Int, String]("Hello"))))(
        //            equalTo(Some(1))
        //          )
        //        })
        //      ),
        test("untilOutputZIO with head sink") {
          val sink: ZSink[Any, Nothing, Int, Nothing, Int, Option[Option[Int]]] =
            ZSink.head[Nothing, Int].untilOutputZIO(h => ZIO.succeed(h.fold(false)(_ >= 10)))
          val assertions = ZIO.foreach(Chunk(1, 3, 7, 20)) { n =>
            assertM(ZStream.fromIterable(1 to 100).rechunk(n).run(sink))(equalTo(Some(Some(10))))
          }
          assertions.map(tst => tst.reduce(_ && _))
        },
        test("untilOutputZIO take sink across multiple chunks") {
          val sink: ZSink[Any, Nothing, Int, Nothing, Int, Option[Chunk[Int]]] =
            ZSink.take[Nothing, Int](4).untilOutputZIO(s => ZIO.succeed(s.sum > 10))

          assertM(ZStream.fromIterable(1 to 8).rechunk(2).run(sink))(equalTo(Some(Chunk(5, 6, 7, 8))))
        },
        test("untilOutputZIO empty stream terminates with none") {
          assertM(
            ZStream.fromIterable(List.empty[Int]).run(ZSink.sum[Nothing, Int].untilOutputZIO(s => ZIO.succeed(s > 0)))
          )(equalTo(None))
        },
        test("untilOutputZIO unsatisfied condition terminates with none") {
          assertM(
            ZStream
              .fromIterable(List(1, 2))
              .run(ZSink.head[Nothing, Int].untilOutputZIO(h => ZIO.succeed(h.fold(false)(_ >= 3))))
          )(equalTo(None))
        },
        suite("flatMap")(
          test("non-empty input") {
            assertM(
              ZStream(1, 2, 3)
                .run(ZSink.head[Nothing, Int].flatMap((x: Option[Int]) => ZSink.succeed(x)))
            )(equalTo(Some(1)))
          },
          test("empty input") {
            assertM(ZStream.empty.run(ZSink.head[Nothing, Int].flatMap(h => ZSink.succeed(h))))(
              equalTo(None)
            )
          },
          test("with leftovers") {
            val headAndCount =
              ZSink.head[Nothing, Int].flatMap((h: Option[Int]) => ZSink.count.map(cnt => (h, cnt)))
            check(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.int)))) { chunks =>
              ZStream.fromChunks(chunks: _*).run(headAndCount).map {
                case (head, count) => {
                  assert(head)(equalTo(chunks.flatten.headOption)) &&
                  assert(count + head.toList.size)(equalTo(chunks.map(_.size.toLong).sum))
                }
              }
            }
          },
          test("leftovers are kept in order") {
            Ref.make(Chunk[Chunk[Int]]()).flatMap { readData =>
              def takeN(n: Int) =
                ZSink.take[Nothing, Int](n).mapZIO(c => readData.update(_ :+ c))

              def taker(data: Chunk[Chunk[Int]], n: Int): (Chunk[Int], Chunk[Chunk[Int]], Boolean) = {
                import scala.collection.mutable
                val buffer   = mutable.Buffer(data: _*)
                val builder  = mutable.Buffer[Int]()
                var wasSplit = false

                while (builder.size < n && buffer.nonEmpty) {
                  val popped = buffer.remove(0)

                  if ((builder.size + popped.size) <= n) builder ++= popped
                  else {
                    val splitIndex  = n - builder.size
                    val (take, ret) = popped.splitAt(splitIndex)
                    builder ++= take
                    buffer.prepend(ret)

                    if (splitIndex > 0)
                      wasSplit = true
                  }
                }

                (Chunk.fromIterable(builder), Chunk.fromIterable(buffer), wasSplit)
              }

              val gen =
                for {
                  sequenceSize <- Gen.int(1, 50)
                  takers       <- Gen.int(1, 5)
                  takeSizes    <- Gen.listOfN(takers)(Gen.int(1, sequenceSize))
                  inputs       <- Gen.chunkOfN(sequenceSize)(ZStreamGen.tinyChunkOf(Gen.int))
                  (expectedTakes, leftoverInputs, wasSplit) = takeSizes.foldLeft((Chunk[Chunk[Int]](), inputs, false)) {
                                                                case ((takenChunks, leftover, _), takeSize) =>
                                                                  val (taken, rest, wasSplit) =
                                                                    taker(leftover, takeSize)
                                                                  (takenChunks :+ taken, rest, wasSplit)
                                                              }
                  expectedLeftovers = if (wasSplit) leftoverInputs.head
                                      else Chunk()
                } yield (inputs, takeSizes, expectedTakes, expectedLeftovers)

              check(gen) { case (inputs, takeSizes, expectedTakes, expectedLeftovers) =>
                val takingSinks = takeSizes.map(takeN(_)).reduce(_ *> _).channel.doneCollect
                val channel     = ZChannel.writeAll(inputs: _*) >>> takingSinks

                (channel.run <*> readData.getAndSet(Chunk())).map { case (leftovers, _, takenChunks) =>
                  assert(leftovers.flatten)(equalTo(expectedLeftovers)) &&
                    assert(takenChunks)(equalTo(expectedTakes))
                }
              }
            }
          } @@ jvmOnly
        ),
        suite("repeat")(
          test("runs until the source is exhausted") {
            assertM(
              ZStream
                .fromChunks(Chunk(1, 2), Chunk(3, 4, 5), Chunk(), Chunk(6, 7), Chunk(8, 9))
                .run(ZSink.take[Nothing, Int](3).repeat)
            )(
              equalTo(Chunk(Chunk(1, 2, 3), Chunk(4, 5, 6), Chunk(7, 8, 9), Chunk.empty))
            )
          },
          test("combinators") {
            assertM(
              ZStream
                .fromChunks(Chunk(1, 2), Chunk(3, 4, 5), Chunk.empty, Chunk(6, 7), Chunk(8, 9))
                .run(ZSink.sum[Nothing, Int].repeat.map(_.sum))
            )(
              equalTo(45)
            )
          },
          test("handles errors") {
            assertM(
              (ZStream
                .fromChunks(Chunk(1, 2)))
                .run(ZSink.fail(()).repeat)
                .either
            )(
              isLeft
            )
          }
        ),
        suite("take")(
          test("take")(
            check(Gen.chunkOf(Gen.small(Gen.chunkOfN(_)(Gen.int))), Gen.int) { (chunks, n) =>
              ZStream
                .fromChunks(chunks: _*)
                .peel(ZSink.take[Nothing, Int](n))
                .flatMap { case (chunk, stream) =>
                  stream.runCollect.toManaged.map { leftover =>
                    assert(chunk)(equalTo(chunks.flatten.take(n))) &&
                    assert(leftover)(equalTo(chunks.flatten.drop(n)))
                  }
                }
                .useNow
            }
          )
        ),
        test("timed") {
          for {
            f <- ZStream.fromIterable(1 to 10).mapZIO(i => Clock.sleep(10.millis).as(i)).run(ZSink.timed).fork
            _ <- TestClock.adjust(100.millis)
            r <- f.join
          } yield assert(r)(isGreaterThanEqualTo(100.millis))
        },
        suite("utf8Decode")(
          test("running with regular strings") {
            check(Gen.string.map(s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_8)))) { bytes =>
              ZStream
                .fromChunk(bytes)
                .run(ZSink.utf8Decode)
                .map(result =>
                  assert(bytes)(isNonEmpty) implies assert(result)(
                    isSome(equalTo(new String(bytes.toArray, StandardCharsets.UTF_8)))
                  )
                )
            }
          },
          test("transducing with regular strings") {
            check(Gen.string.map(s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_8)))) { byteChunk =>
              ZStream
                .fromChunk(byteChunk)
                .transduce(ZSink.utf8Decode.map(_.getOrElse("")))
                .run(ZSink.mkString)
                .map { result =>
                  assertTrue(byteChunk == Chunk.fromArray(result.getBytes(StandardCharsets.UTF_8)))
                }
            }
          },
          test("transducing with regular strings with BOM") {
            testDecoderWithRandomStringUsing(
              ZSink.utf8Decode,
              StandardCharsets.UTF_8,
              BOM.Utf8 ++ _
            )
          },
          test("transducing with regular strings, using `utfDecode`") {
            testDecoderWithRandomStringUsing(
              ZSink.utfDecode,
              StandardCharsets.UTF_8
            )
          },
          test("empty byte chunk results with None") {
            val bytes = Chunk()
            ZStream
              .fromChunk(bytes)
              .run(ZSink.utf8Decode)
              .map(assert(_)(isNone))
          },
          test("incomplete chunk 1") {
            val data = Array(0xc2.toByte, 0xa2.toByte)

            ZStream
              .fromChunks(BOM.Utf8, Chunk(data(0)), Chunk(data(1)))
              .run(ZSink.utf8Decode.exposeLeftover)
              .map { case (string, bytes) =>
                assert(string)(isSome(equalTo(new String(data, StandardCharsets.UTF_8)))) &&
                  assert(bytes)(isEmpty)
              }
          },
          test("incomplete chunk 2") {
            val data = Array(0xe0.toByte, 0xa4.toByte, 0xb9.toByte)

            ZStream
              .fromChunks(BOM.Utf8, Chunk(data(0), data(1)), Chunk(data(2)))
              .run(ZSink.utf8Decode.exposeLeftover)
              .map { case (string, bytes) =>
                assert(string)(isSome(equalTo(new String(data, StandardCharsets.UTF_8)))) &&
                  assert(bytes)(isEmpty)
              }
          },
          test("incomplete chunk 3") {
            val data = Array(0xf0.toByte, 0x90.toByte, 0x8d.toByte, 0x88.toByte)

            ZStream
              .fromChunks(BOM.Utf8, Chunk(data(0), data(1), data(2)), Chunk(data(3)))
              .run(ZSink.utf8Decode.exposeLeftover)
              .map { case (string, bytes) =>
                assert(string)(isSome(equalTo(new String(data, StandardCharsets.UTF_8)))) &&
                  assert(bytes)(isEmpty)
              }
          },
          test("chunk with leftover") {
            ZStream
              .fromChunk(Chunk(0xf0.toByte, 0x90.toByte, 0x8d.toByte, 0x88.toByte, 0xf0.toByte, 0x90.toByte))
              .run(ZSink.utf8Decode)
              .map { result =>
                assert(result.map(s => Chunk.fromArray(s.getBytes)))(
                  isSome(equalTo(Chunk.fromArray(Array(0xf0.toByte, 0x90.toByte, 0x8d.toByte, 0x88.toByte))))
                )
              }
          },
          test("handle byte order mark") {
            check(Gen.string) { s =>
              ZStream
                .fromChunk(BOM.Utf8 ++ Chunk.fromArray(s.getBytes(StandardCharsets.UTF_8)))
                .transduce(ZSink.utf8Decode)
                .runCollect
                .map { result =>
                  assert(result.collect { case Some(s) => s }.mkString)(equalTo(s))
                }
            }
          },
          test("handle byte order mark, using `utfDecode`") {
            check(Gen.string) { s =>
              ZStream
                .fromChunk(BOM.Utf8 ++ Chunk.fromArray(s.getBytes(StandardCharsets.UTF_8)))
                .transduce(ZSink.utfDecode)
                .runCollect
                .map { result =>
                  assert(result.collect { case Some(s) => s }.mkString)(equalTo(s))
                }
            }
          }
        ),
        suite("iso_8859_1")(
          test("ISO-8859-1 strings")(check(Gen.iso_8859_1) { s =>
            ZStream
              .fromChunk(Chunk.fromArray(s.getBytes(StandardCharsets.ISO_8859_1)))
              .transduce(ZSink.iso_8859_1Decode.map(_.getOrElse("")))
              .mkString
              .map(result => assertTrue(result == s))
          })
        ),
        suite("utf16BEDecode")(
          test("regular strings") {
            testDecoderWithRandomStringUsing(
              ZSink.utf16BEDecode,
              StandardCharsets.UTF_16BE
            )
          },
          test("regular strings, using `utfDecode`") {
            testDecoderWithRandomStringUsing(
              ZSink.utfDecode,
              StandardCharsets.UTF_16BE,
              BOM.Utf16BE ++ _
            )
          }
        ),
        suite("utf16LEDecode")(
          test("regular strings") {
            testDecoderWithRandomStringUsing(
              ZSink.utf16LEDecode,
              StandardCharsets.UTF_16LE
            )
          },
          test("regular strings, using `utfDecode`") {
            testDecoderWithRandomStringUsing(
              ZSink.utfDecode,
              StandardCharsets.UTF_16LE,
              BOM.Utf16LE ++ _
            )
          }
        ),
        suite("utf16Decode")(
          test("regular strings") {
            check(Gen.string.map(s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16)))) { byteChunk =>
              ZStream
                .fromChunk(byteChunk)
                .transduce(ZSink.utf16Decode.map(_.getOrElse("")))
                .mkString
                .map { result =>
                  assertTrue(byteChunk == Chunk.fromArray(result.getBytes(StandardCharsets.UTF_16)))
                }
            }
          },
          test("no BOM - parse as big endian") {
            check(
              Gen.string
                .filter(_.nonEmpty)
                .map(s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16BE)))
            ) { byteChunk =>
              ZStream
                .fromChunk(byteChunk)
                .transduce(ZSink.utf16Decode.map(_.getOrElse("")))
                .mkString
                .map { result =>
                  assertTrue(byteChunk == Chunk.fromArray(result.getBytes(StandardCharsets.UTF_16BE)))
                }
            }
          },
          test("big endian") {
            check(Gen.string.map(s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16BE)))) { s =>
              ZStream
                .fromChunks(
                  BOM.Utf16BE,
                  s
                )
                .transduce(ZSink.utf16Decode.map(_.getOrElse("")))
                .mkString
                .map(result => assertTrue(s == Chunk.fromArray(result.getBytes(StandardCharsets.UTF_16BE))))
            }
          },
          test("little endian") {
            check(Gen.string.map(s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16LE)))) { s =>
              ZStream
                .fromChunks(
                  BOM.Utf16LE,
                  s
                )
                .transduce(ZSink.utf16Decode.map(_.getOrElse("")))
                .mkString
                .map(result => assertTrue(s == Chunk.fromArray(result.getBytes(StandardCharsets.UTF_16LE))))
            }
          }
        ),
        suite("utf32BEDecode")(
          test("regular strings") {
            testDecoderWithRandomStringUsing(
              ZSink.utf32BEDecode,
              CharsetUtf32BE
            )
          },
          test("regular strings, using `utfDecode`") {
            testDecoderWithRandomStringUsing(
              ZSink.utfDecode,
              CharsetUtf32BE,
              BOM.Utf32BE ++ _
            )
          }
        ) @@ runOnlyIfSupporting("UTF-32BE"),
        suite("utf32LEDecode")(
          test("regular strings") {
            testDecoderWithRandomStringUsing(
              ZSink.utf32LEDecode,
              CharsetUtf32LE
            )
          },
          test("regular strings, using `utfDecode`") {
            testDecoderWithRandomStringUsing(
              ZSink.utfDecode,
              CharsetUtf32LE,
              BOM.Utf32LE ++ _
            )
          }
        ) @@ runOnlyIfSupporting("UTF-32LE"),
        suite("utf32Decode")(
          test("regular strings") {
            testDecoderWithRandomStringUsing(
              ZSink.utf32Decode,
              CharsetUtf32
            )
          },
          test("no BOM - parse as big endian") {
            testDecoderWithRandomStringUsing(
              ZSink.utf32Decode,
              CharsetUtf32BE
            )
          },
          test("big endian") {
            testDecoderWithRandomStringUsing(
              ZSink.utf32Decode,
              CharsetUtf32BE,
              BOM.Utf32BE ++ _
            )
          },
          test("little endian") {
            testDecoderWithRandomStringUsing(
              ZSink.utf32Decode,
              CharsetUtf32LE,
              BOM.Utf32LE ++ _
            )
          }
        ) @@ runOnlyIfSupporting("UTF-32"),
        suite("usASCII")(
          test("US-ASCII strings") {
            check(Gen.chunkOf(Gen.asciiString.map(s => Chunk.fromArray(s.getBytes(StandardCharsets.US_ASCII))))) {
              chunks =>
                ZStream
                  .fromChunks(chunks: _*)
                  .transduce(ZSink.usASCIIDecode.map(_.getOrElse("")))
                  .mkString
                  .map(s => assertTrue(chunks.flatten == Chunk.fromArray(s.getBytes(StandardCharsets.US_ASCII))))
            }
          }
        )
      )
    )
  }
}
