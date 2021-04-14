package zio.stream.experimental

import zio._
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

object ZSinkSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec: ZSpec[Environment, Failure] = {
    suite("ZSinkSpec")(
      suite("Constructors")(
        suite("collectAllN")(
          testM("respects the given limit") {
            ZStream
              .fromChunk(Chunk(1, 2, 3, 4))
              .transduce(ZSink.collectAllN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Chunk(1, 2, 3), Chunk(4)))))
          },
          testM("produces empty trailing chunks") {
            ZStream
              .fromChunk(Chunk(1, 2, 3, 4))
              .transduce(ZSink.collectAllN[Nothing, Int](4))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Chunk(1, 2, 3, 4), Chunk()))))
          },
          testM("handles empty input") {
            ZStream
              .fromChunk(Chunk.empty: Chunk[Int])
              .transduce(ZSink.collectAllN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Chunk()))))
          }
        ),
        testM("collectAllToSet")(
          assertM(
            ZStream(1, 2, 3, 3, 4)
              .run(ZSink.collectAllToSet[Nothing, Int])
          )(equalTo(Set(1, 2, 3, 4)))
        ),
        suite("collectAllToSetN")(
          testM("respect the given limit") {
            ZStream
              .fromChunks(Chunk(1, 2, 1), Chunk(2, 3, 3, 4))
              .transduce(ZSink.collectAllToSetN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Set(1, 2, 3), Set(4)))))
          },
          testM("handles empty input") {
            ZStream
              .fromChunk(Chunk.empty: Chunk[Int])
              .transduce(ZSink.collectAllToSetN[Nothing, Int](3))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Set.empty[Int]))))
          }
        ),
        testM("collectAllToMap")(
          assertM(
            ZStream
              .range(0, 10)
              .run(ZSink.collectAllToMap((_: Int) % 3)(_ + _))
          )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
        ),
        suite("collectAllToMapN")(
          testM("respects the given limit") {
            ZStream
              .fromChunk(Chunk(1, 1, 2, 2, 3, 2, 4, 5))
              .transduce(ZSink.collectAllToMapN(2)((_: Int) % 3)(_ + _))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Map(1 -> 2, 2 -> 4), Map(0 -> 3, 2 -> 2), Map(1 -> 4, 2 -> 5)))))
          },
          testM("collects as long as map size doesn't exceed the limit") {
            ZStream
              .fromChunks(Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6, 7, 8, 9))
              .transduce(ZSink.collectAllToMapN(3)((_: Int) % 3)(_ + _))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Map(0 -> 18, 1 -> 12, 2 -> 15)))))
          },
          testM("handles empty input") {
            ZStream
              .fromChunk(Chunk.empty: Chunk[Int])
              .transduce(ZSink.collectAllToMapN(2)((_: Int) % 3)(_ + _))
              .runCollect
              .map(assert(_)(equalTo(Chunk(Map.empty[Int, Int]))))
          }
        ),
        suite("accessSink")(
          testM("accessSink") {
            assertM(
              ZStream("ignore this")
                .run(ZSink.accessSink[String](ZSink.succeed(_)).provide("use this"))
            )(equalTo("use this"))
          }
        ),
        //      suite("collectAllWhileWith")(
        //        testM("example 1") {
        //          ZIO
        //            .foreach(List(1, 3, 20)) { chunkSize =>
        //              assertM(
        //                Stream
        //                  .fromIterable(1 to 10)
        //                  .chunkN(chunkSize)
        //                  .run(ZSink.sum[Int].collectAllWhileWith(-1)((s: Int) => s == s)(_ + _))
        //              )(equalTo(54))
        //            }
        //            .map(_.reduce(_ && _))
        //        },
        //        testM("example 2") {
        //          val sink: ZSink[Any, Nothing, Int, Int, List[Int]] = ZSink
        //            .head[Int]
        //            .collectAllWhileWith[List[Int]](Nil)((a: Option[Int]) => a.fold(true)(_ < 5))(
        //              (a: List[Int], b: Option[Int]) => a ++ b.toList
        //            )
        //          val stream = Stream.fromIterable(1 to 100)
        //          assertM((stream ++ stream).chunkN(3).run(sink))(equalTo(List(1, 2, 3, 4)))
        //        }
        //      ),
        //      testM("head")(
        //        checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
        //          val headOpt = ZStream.fromChunks(chunks: _*).run(ZSink.head[Int])
        //          assertM(headOpt)(equalTo(chunks.flatMap(_.toSeq).headOption))
        //        }
        //      ),
        testM("last")(
          checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
            val lastOpt = ZStream.fromChunks(chunks: _*).run(ZSink.last)
            assertM(lastOpt)(equalTo(chunks.flatMap(_.toSeq).lastOption))
          }
        ),
        suite("managed")(
          testM("happy path") {
            for {
              closed <- Ref.make[Boolean](false)
              res     = ZManaged.make(ZIO.succeed(100))(_ => closed.set(true))
              sink = ZSink.managed[Any, Any, Any, Any, Int, Nothing, (Long, Boolean)](res)(m =>
                       ZSink.count.mapM(cnt => closed.get.map(cl => (cnt + m, cl)))
                     )
              resAndState <- ZStream(1, 2, 3).run(sink)
              finalState  <- closed.get
            } yield {
              assert(resAndState._1)(equalTo(103L)) && assert(resAndState._2)(isFalse) && assert(finalState)(isTrue)
            }
          },
          testM("sad path") {
            for {
              closed     <- Ref.make[Boolean](false)
              res         = ZManaged.make(ZIO.succeed(100))(_ => closed.set(true))
              sink        = ZSink.managed[Any, String, Any, String, Int, Nothing, String](res)(_ => ZSink.succeed("ok"))
              r          <- ZStream.fail("fail").run(sink).either
              finalState <- closed.get
            } yield assert(r)(equalTo(Right("ok"))) && assert(finalState)(isTrue)
          }
        )
      ),
      testM("map")(
        assertM(ZStream.range(1, 10).run(ZSink.succeed(1).map(_.toString)))(
          equalTo("1")
        )
      ),
      suite("mapM")(
        testM("happy path")(
          assertM(ZStream.range(1, 10).run(ZSink.succeed(1).mapM(e => ZIO.succeed(e + 1))))(
            equalTo(2)
          )
        ),
        testM("failure")(
          assertM(ZStream.range(1, 10).run(ZSink.succeed(1).mapM(_ => ZIO.fail("fail"))).flip)(
            equalTo("fail")
          )
        )
      ),
      testM("mapError")(
        assertM(ZStream.range(1, 10).run(ZSink.fail("fail").mapError(s => s + "!")).either)(
          equalTo(Left("fail!"))
        )
      ),
      testM("as")(
        assertM(ZStream.range(1, 10).run(ZSink.succeed(1).as("as")))(
          equalTo("as")
        )
      ),
      suite("contramap")(
        testM("happy path") {
          val parser = ZSink.collectAll[Nothing, Int].contramap[String](_.toInt)
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        testM("error") {
          val parser = ZSink.fail("Ouch").contramap[String](_.toInt)
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramapChunks")(
        testM("happy path") {
          val parser = ZSink.collectAll[Nothing, Int].contramapChunks[String](_.map(_.toInt))
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        testM("error") {
          val parser = ZSink.fail("Ouch").contramapChunks[String](_.map(_.toInt))
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramapM")(
        testM("happy path") {
          val parser = ZSink.collectAll[Throwable, Int].contramapM[Any, Throwable, String](a => ZIO.effect(a.toInt))
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        testM("error") {
          val parser = ZSink.fail("Ouch").contramapM[Any, Throwable, String](a => ZIO.effect(a.toInt))
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("error in transformation") {
          val parser = ZSink.collectAll[Throwable, Int].contramapM[Any, Throwable, String](a => ZIO.effect(a.toInt))
          assertM(ZStream("1", "a").run(parser).either)(isLeft(hasMessage(equalTo("For input string: \"a\""))))
        } @@ zioTag(errors)
      ),
      suite("contramapChunksM")(
        testM("happy path") {
          val parser =
            ZSink.collectAll[Throwable, Int].contramapChunksM[Any, Throwable, String](_.mapM(a => ZIO.effect(a.toInt)))
          assertM(ZStream("1", "2", "3").run(parser))(equalTo(Chunk(1, 2, 3)))
        },
        testM("error") {
          val parser = ZSink.fail("Ouch").contramapChunksM[Any, Throwable, String](_.mapM(a => ZIO.effect(a.toInt)))
          assertM(ZStream("1", "2", "3").run(parser).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("error in transformation") {
          val parser =
            ZSink.collectAll[Throwable, Int].contramapChunksM[Any, Throwable, String](_.mapM(a => ZIO.effect(a.toInt)))
          assertM(ZStream("1", "a").run(parser).either)(isLeft(hasMessage(equalTo("For input string: \"a\""))))
        } @@ zioTag(errors)
      ),
      testM("collectAllWhile") {
        val sink   = ZSink.collectAllWhile[Nothing, Int](_ < 5)
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = ZStream.fromChunks(input: _*).transduce(sink).runCollect
        assertM(result)(equalTo(Chunk(Chunk(3, 4), Chunk(), Chunk(), Chunk(2, 3, 4), Chunk(), Chunk(), Chunk(4, 3, 2))))
      },
      testM("collectAllWhileM") {
        val sink   = ZSink.collectAllWhileM[Any, Nothing, Int]((i: Int) => ZIO.succeed(i < 5))
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = ZStream.fromChunks(input: _*).transduce(sink).runCollect
        assertM(result)(equalTo(Chunk(Chunk(3, 4), Chunk(), Chunk(), Chunk(2, 3, 4), Chunk(), Chunk(), Chunk(4, 3, 2))))
      },
      testM("foldLeft equivalence with Chunk#foldLeft")(
        checkM(
          Gen.small(ZStreamGen.pureStreamGen(Gen.anyInt, _)),
          Gen.function2(Gen.anyString),
          Gen.anyString
        ) { (s, f, z) =>
          for {
            xs <- s.run(ZSink.foldLeft(z)(f))
            ys <- s.runCollect.map(_.foldLeft(z)(f))
          } yield assert(xs)(equalTo(ys))
        }
      ),
      suite("foldM")(
        testM("empty")(
          assertM(
            ZStream.empty
              .transduce(
                ZSink.foldM(0)(_ => true)((x, y: Int) => ZIO.succeed(x + y))
              )
              .runCollect
          )(equalTo(Chunk(0)))
        ),
        testM("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                        .transduce(ZSink.foldM(0)(_ => true) { (_, a: Int) =>
                          effects.update(a :: _) *> UIO.succeed(30)
                        })
                        .runCollect
              result <- effects.get
            } yield exit -> result).run

          (assertM(run(empty))(succeeds(equalTo((Chunk(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((Chunk(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((Chunk(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map { case (((r1, r2), r3), r4) =>
            r1 && r2 && r3 && r4
          }
        },
        testM("equivalence with List#foldLeft") {
          val ioGen = ZStreamGen.successes(Gen.anyString)
          checkM(Gen.small(ZStreamGen.pureStreamGen(Gen.anyInt, _)), Gen.function2(ioGen), ioGen) { (s, f, z) =>
            for {
              sinkResult <- z.flatMap(z => s.run(ZSink.foldLeftM(z)(f)))
              foldResult <- s.runFold(List[Int]())((acc, el) => el :: acc)
                              .map(_.reverse)
                              .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
                              .run
            } yield assert(foldResult.succeeded)(isTrue) implies assert(foldResult)(succeeds(equalTo(sinkResult)))
          }
        }
      ),
      suite("fold")(
        testM("empty")(
          assertM(
            ZStream.empty
              .transduce(ZSink.fold[Nothing, Int, Int](0)(_ => true)(_ + _))
              .runCollect
          )(equalTo(Chunk(0)))
        ),
        testM("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                        .transduce(ZSink.foldM(0)(_ => true) { (_, a: Int) =>
                          effects.update(a :: _) *> UIO.succeed(30)
                        })
                        .runCollect
              result <- effects.get
            } yield (exit, result)).run

          (assertM(run(empty))(succeeds(equalTo((Chunk(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((Chunk(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((Chunk(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map { case (((r1, r2), r3), r4) =>
            r1 && r2 && r3 && r4
          }
        },
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Nothing, Int, Int](0)(_ <= 5)((a, b) => a + b)))(equalTo(6))
        ),
        testM("immediate termination")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Nothing, Int, Int](0)(_ <= -1)((a, b) => a + b)))(equalTo(0))
        ),
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Nothing, Int, Int](0)(_ <= 500)((a, b) => a + b)))(equalTo(45))
        )
      ),
      testM("foldUntil")(
        assertM(
          ZStream[Long](1, 1, 1, 1, 1, 1)
            .transduce(ZSink.foldUntil(0L, 3)(_ + (_: Long)))
            .runCollect
        )(equalTo(Chunk(3L, 3L, 0L)))
      ),
      testM("foldUntilM")(
        assertM(
          ZStream[Long](1, 1, 1, 1, 1, 1)
            .transduce(ZSink.foldUntilM(0L, 3)((s, a: Long) => UIO.succeedNow(s + a)))
            .runCollect
        )(equalTo(Chunk(3L, 3L, 0L)))
      ),
      testM("foldWeighted")(
        assertM(
          ZStream[Long](1, 5, 2, 3)
            .transduce(
              ZSink.foldWeighted(List[Long]())((_, x: Long) => x * 2, 12)((acc, el) => el :: acc).map(_.reverse)
            )
            .runCollect
        )(equalTo(Chunk(List(1L, 5L), List(2L, 3L))))
      ),
      suite("foldWeightedDecompose")(
        testM("simple example")(
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
        testM("empty stream")(
          assertM(
            ZStream.empty
              .transduce(
                ZSink.foldWeightedDecompose(0)((_, x: Int) => x.toLong, 1000, Chunk.single(_: Int))(_ + _)
              )
              .runCollect
          )(equalTo(Chunk(0)))
        )
      ),
      testM("foldWeightedM")(
        assertM(
          ZStream[Long](1, 5, 2, 3)
            .transduce(
              ZSink
                .foldWeightedM(List.empty[Long])((_, a: Long) => UIO.succeedNow(a * 2), 12)((acc, el) =>
                  UIO.succeedNow(el :: acc)
                )
                .map(_.reverse)
            )
            .runCollect
        )(equalTo(Chunk(List(1L, 5L), List(2L, 3L))))
      ),
      suite("foldWeightedDecomposeM")(
        testM("simple example")(
          assertM(
            ZStream(1, 5, 1)
              .transduce(
                ZSink
                  .foldWeightedDecomposeM(List.empty[Int])(
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
        testM("empty stream")(
          assertM(
            ZStream.empty
              .transduce(
                ZSink.foldWeightedDecomposeM[Any, Nothing, Int, Int](0)(
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
        testM("handles leftovers") {
          val s =
            ZSink
              .fail("boom")
              .foldM(err => ZSink.collectAll[String, Int].map(c => (c, err)), _ => sys.error("impossible"))
          assertM(ZStream(1, 2, 3).run(s))(equalTo((Chunk(1, 2, 3), "boom")))
        }
      ),
      // suite("foreach")(
      //   testM("preserves leftovers in case of failure") {
      //     for {
      //       acc <- Ref.make[Int](0)
      //       s    = ZSink.foreach[Any, String, Int]((i: Int) => if (i == 4) ZIO.fail("boom") else acc.update(_ + i))
      //       sink = s.foldM(_ => ZSink.collectAll[String, Int], _ => sys.error("impossible"))
      //       leftover <- ZStream.fromChunks(Chunk(1, 2), Chunk(3, 4, 5)).run(sink)
      //       sum      <- acc.get
      //     } yield {
      //       assert(sum)(equalTo(6)) && assert(leftover)(equalTo(Chunk(5)))
      //     }
      //   }
      // ),
      suite("foreachWhile")(
        testM("handles leftovers") {
          val leftover = ZStream
            .fromIterable(1 to 5)
            .run(ZSink.foreachWhile((n: Int) => ZIO.succeed(n <= 3)).exposeLeftover)
            .map(_._2)
          assertM(leftover)(equalTo(Chunk(4, 5)))
        }
      ),
      suite("fromEffect")(
        testM("result is ok") {
          val s = ZSink.fromEffect(ZIO.succeed("ok"))
          assertM(ZStream(1, 2, 3).run(s))(
            equalTo("ok")
          )
        }
      ),
      suite("succeed")(
        testM("result is ok") {
          assertM(ZStream(1, 2, 3).run(ZSink.succeed("ok")))(
            equalTo("ok")
          )
        }
      ),
      suite("Combinators")(
        //      testM("raceBoth") {
        //        checkM(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
        //          val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
        //          sinkRaceLaw(ZStream.fromIterable(Random.shuffle(stream)), findSink(20), findSink(40))
        //        }
        //      },
        //      suite("zipWithPar")(
        //        testM("coherence") {
        //          checkM(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
        //            val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
        //            SinkUtils
        //              .zipParLaw(ZStream.fromIterable(Random.shuffle(stream)), findSink(20), findSink(40))
        //          }
        //        },
        //        suite("zipRight (*>)")(
        //          testM("happy path") {
        //            assertM(ZStream(1, 2, 3).run(ZSink.head.zipParRight(ZSink.succeed[Int, String]("Hello"))))(
        //              equalTo(("Hello"))
        //            )
        //          }
        //        ),
        //        suite("zipWith")(testM("happy path") {
        //          assertM(ZStream(1, 2, 3).run(ZSink.head.zipParLeft(ZSink.succeed[Int, String]("Hello"))))(
        //            equalTo(Some(1))
        //          )
        //        })
        //      ),
        //      testM("untilOutputM") {
        //        val sink: ZSink[Any, Nothing, Int, Int, Option[Option[Int]]] =
        //          ZSink.head[Int].untilOutputM(h => ZIO.succeed(h.fold(false)(_ >= 10)))
        //        val assertions = ZIO.foreach(Chunk(1, 3, 7, 20)) { n =>
        //          assertM(Stream.fromIterable(1 to 100).chunkN(n).run(sink))(equalTo(Some(Some(10))))
        //        }
        //        assertions.map(tst => tst.reduce(_ && _))
        //      },
        suite("flatMap")(
          testM("non-empty input") {
            assertM(
              ZStream(1, 2, 3)
                .run(ZSink.head[Nothing, Int].flatMap((x: Option[Int]) => ZSink.succeed(x)))
            )(equalTo(Some(1)))
          },
          testM("empty input") {
            assertM(ZStream.empty.run(ZSink.head[Nothing, Int].flatMap(h => ZSink.succeed(h))))(
              equalTo(None)
            )
          },
          testM("with leftovers") {
            val headAndCount =
              ZSink.head[Nothing, Int].flatMap((h: Option[Int]) => ZSink.count.map(cnt => (h, cnt)))
            checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
              ZStream.fromChunks(chunks: _*).run(headAndCount).map {
                case (head, count) => {
                  assert(head)(equalTo(chunks.flatten.headOption)) &&
                  assert(count + head.toList.size)(equalTo(chunks.map(_.size.toLong).sum))
                }
              }
            }
          }
        ),
        testM("take")(
          checkM(Gen.chunkOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt))), Gen.anyInt) { (chunks, n) =>
            ZStream
              .fromChunks(chunks: _*)
              .peel(ZSink.take[Nothing, Int](n))
              .flatMap { case (chunk, stream) =>
                stream.runCollect.toManaged_.map { leftover =>
                  assert(chunk)(equalTo(chunks.flatten.take(n))) &&
                  assert(leftover)(equalTo(chunks.flatten.drop(n)))
                }
              }
              .useNow
          }
        ),
        testM("timed") {
          for {
            f <- ZStream.fromIterable(1 to 10).mapM(i => clock.sleep(10.millis).as(i)).run(ZSink.timed).fork
            _ <- TestClock.adjust(100.millis)
            r <- f.join
          } yield assert(r)(isGreaterThanEqualTo(100.millis))
        }
      )
    )
  }
}
