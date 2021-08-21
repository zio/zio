package zio.stream

import zio.stream.SinkUtils.{findSink, sinkRaceLaw}
import zio.stream.ZStreamGen._
import zio.test.Assertion.{equalTo, isFalse, isGreaterThanEqualTo, isLeft, isTrue, succeeds}
import zio.test.environment.TestClock
import zio.test.{assertM, _}
import zio.{ZIOBaseSpec, _}

import scala.util.Random

object ZSinkSpec extends ZIOBaseSpec {
  def spec: ZSpec[Environment, Failure] = suite("ZSinkSpec")(
    suite("Constructors")(
      test("collectAllToSet")(
        assertM(
          ZStream(1, 2, 3, 3, 4)
            .run(ZSink.collectAllToSet[Int])
        )(equalTo(Set(1, 2, 3, 4)))
      ),
      test("collectAllToMap")(
        assertM(
          ZStream
            .range(0, 10)
            .run(ZSink.collectAllToMap((_: Int) % 3)(_ + _))
        )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
      ),
      suite("accessSink")(
        test("accessSink") {
          assertM(
            ZStream("ignore this")
              .run(ZSink.accessSink[String](ZSink.succeed[String, String](_)).provide("use this"))
          )(equalTo("use this"))
        }
      ),
      suite("collectAllWhileWith")(
        test("example 1") {
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
        test("example 2") {
          val sink: ZSink[Any, Nothing, Int, Int, List[Int]] = ZSink
            .head[Int]
            .collectAllWhileWith[List[Int]](Nil)((a: Option[Int]) => a.fold(true)(_ < 5))(
              (a: List[Int], b: Option[Int]) => a ++ b.toList
            )
          val stream = Stream.fromIterable(1 to 100)
          assertM((stream ++ stream).chunkN(3).run(sink))(equalTo(List(1, 2, 3, 4)))
        }
      ),
      test("head")(
        checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.int)))) { chunks =>
          val headOpt = ZStream.fromChunks(chunks: _*).run(ZSink.head[Int])
          assertM(headOpt)(equalTo(chunks.flatMap(_.toSeq).headOption))
        }
      ),
      test("last")(
        checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.int)))) { chunks =>
          val lastOpt = ZStream.fromChunks(chunks: _*).run(ZSink.last[Int])
          assertM(lastOpt)(equalTo(chunks.flatMap(_.toSeq).lastOption))
        }
      ),
      suite("managed")(
        test("happy path") {
          for {
            closed <- Ref.make[Boolean](false)
            res     = ZManaged.acquireReleaseWith(ZIO.succeed(100))(_ => closed.set(true))
            sink: ZSink[Any, Nothing, Int, Int, (Long, Boolean)] =
              ZSink.managed(res)(m => ZSink.count.mapZIO(cnt => closed.get.map(cl => (cnt + m, cl))))
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
            sink        = ZSink.managed(res)(_ => ZSink.succeed[Int, String]("ok"))
            r          <- ZStream.fail("fail").run(sink).either
            finalState <- closed.get
          } yield assert(r)(equalTo(Left("fail"))) && assert(finalState)(isTrue)
        }
      ),
      test("foldLeft")(
        checkM(
          Gen.small(pureStreamGen(Gen.int, _)),
          Gen.function2(Gen.string),
          Gen.string
        ) { (s, f, z) =>
          for {
            xs <- s.run(ZSink.foldLeft(z)(f))
            ys <- s.runCollect.map(_.foldLeft(z)(f))
          } yield assert(xs)(equalTo(ys))
        }
      ),
      test("mapError")(
        assertM(ZStream.range(1, 10).run(ZSink.fail[String, Int]("fail").mapError(s => s + "!")).either)(
          equalTo(Left("fail!"))
        )
      ),
      suite("fold")(
        test("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= 5)((a, b) => a + b)))(equalTo(6))
        ),
        test("immediate termination")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= -1)((a, b) => a + b)))(equalTo(0))
        ),
        test("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= 500)((a, b) => a + b)))(equalTo(45))
        )
      ),
      suite("fail")(
        test("handles leftovers") {
          val s: ZSink[Any, Nothing, Int, Nothing, (Chunk[Int], String)] =
            ZSink
              .fail[String, Int]("boom")
              .foldSink(err => ZSink.collectAll.map(c => (c, err)), _ => sys.error("impossible"))
          assertM(ZStream(1, 2, 3).run(s))(equalTo((Chunk(1, 2, 3), "boom")))
        }
      ),
      suite("foldZIO")(
        test("foldZIO") {
          val ioGen = successes(Gen.string)
          checkM(Gen.small(pureStreamGen(Gen.int, _)), Gen.function2(ioGen), ioGen) { (s, f, z) =>
            for {
              sinkResult <- z.flatMap(z => s.run(ZSink.foldLeftZIO(z)(f)))
              foldResult <- s.fold(List[Int]())((acc, el) => el :: acc)
                              .map(_.reverse)
                              .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
                              .exit
            } yield assert(foldResult.isSuccess)(isTrue) implies assert(foldResult)(succeeds(equalTo(sinkResult)))
          }
        }
      ),
      suite("foreach")(
        test("preserves leftovers in case of failure") {
          for {
            acc <- Ref.make[Int](0)
            s    = ZSink.foreach[Any, String, Int]((i: Int) => if (i == 4) ZIO.fail("boom") else acc.update(_ + i))
            sink: ZSink[Any, Nothing, Int, Nothing, Chunk[Int]] =
              s
                .foldSink(_ => ZSink.collectAll, _ => sys.error("impossible"))
            leftover <- ZStream.fromChunks(Chunk(1, 2), Chunk(3, 4, 5)).run(sink)
            sum      <- acc.get
          } yield {
            assert(sum)(equalTo(6)) && assert(leftover)(equalTo(Chunk(5)))
          }
        }
      ),
      suite("foreachWhile")(
        test("handles leftovers") {
          val leftover = ZStream
            .fromIterable(1 to 5)
            .run(ZSink.foreachWhile((n: Int) => ZIO.succeed(n <= 3)).exposeLeftover)
            .map(_._2)
          assertM(leftover)(equalTo(Chunk(4, 5)))
        }
      ),
      suite("fromEffect")(
        test("handles leftovers (happy)") {
          val s = ZSink.fromZIO[Any, Nothing, Int, String](ZIO.succeed("ok"))
          assertM(ZStream(1, 2, 3).run(s.exposeLeftover))(
            equalTo(("ok", Chunk(1, 2, 3)))
          )
        }
      ),
      suite("fromQueue")(
        test("enqueues all elements") {
          checkM(pureStreamOfInts) { stream =>
            for {
              queue          <- ZQueue.unbounded[Int]
              streamElements <- stream.run(ZSink.fromQueue(queue) <&> ZSink.collectAll)
              queueElements  <- queue.takeAll
            } yield assert(queueElements)(equalTo(streamElements))
          }
        },
        test("fails if offering to the queue fails") {
          for {
            queue       <- ZQueue.unbounded[Int]
            exception    = new Exception
            failingQueue = queue.contramapZIO[Any, Exception, Int](_ => ZIO.fail(exception))
            queueSink    = ZSink.fromQueue(failingQueue)
            stream       = Stream(1)
            result      <- stream.run(queueSink).either
          } yield assert(result)(isLeft(equalTo(exception)))
        }
      ),
      suite("succeed")(
        test("handles leftovers") {
          assertM(ZStream(1, 2, 3).run(ZSink.succeed[Int, String]("ok").exposeLeftover))(
            equalTo(("ok", Chunk(1, 2, 3)))
          )
        }
      ),
      suite("fromQueueWithShutdown")(
        test("enqueues all elements and shuts down the queue when the stream completes") {
          checkM(Gen.chunkOfBounded(1, 5)(Gen.int)) { elements =>
            for {
              sourceQueue         <- ZQueue.unbounded[Int]
              targetQueue         <- ZQueue.unbounded[Int]
              stream               = ZStream.fromQueue(sourceQueue)
              streamCompleted     <- Promise.make[Nothing, Unit]
              _                   <- stream.run(ZSink.fromQueueWithShutdown(targetQueue)).flatMap(streamCompleted.succeed).fork
              _                   <- sourceQueue.offerAll(elements)
              queueElements       <- targetQueue.takeBetween(elements.size, elements.size)
              _                   <- sourceQueue.shutdown
              _                   <- streamCompleted.await
              targetQueueShutdown <- targetQueue.isShutdown
            } yield assert(queueElements)(equalTo(elements)) &&
              assert(targetQueueShutdown)(isTrue)
          }
        },
        test("fails if offering to the queue fails") {
          for {
            queue       <- ZQueue.unbounded[Int]
            exception    = new Exception
            failingQueue = queue.contramapZIO[Any, Exception, Int](_ => ZIO.fail(exception))
            queueSink    = ZSink.fromQueueWithShutdown(failingQueue)
            stream       = Stream(1)
            result      <- stream.run(queueSink).either
          } yield assert(result)(isLeft(equalTo(exception)))
        },
        test("shuts down the queue if the stream fails") {
          for {
            queue      <- ZQueue.unbounded[Int]
            exception   = new Exception
            queueSink   = ZSink.fromQueueWithShutdown(queue)
            stream      = Stream.fail(exception)
            result     <- stream.run(queueSink).either
            isShutdown <- queue.isShutdown
          } yield assert(result)(isLeft(equalTo(exception))) &&
            assert(isShutdown)(isTrue)
        }
      )
    ),
    suite("Combinators")(
      test("raceBoth") {
        checkM(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
          val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
          sinkRaceLaw(ZStream.fromIterable(Random.shuffle(stream)), findSink(20), findSink(40))
        }
      },
      suite("zipWithPar")(
        test("coherence") {
          checkM(Gen.listOf(Gen.int(0, 10)), Gen.boolean, Gen.boolean) { (ints, success1, success2) =>
            val stream = ints ++ (if (success1) List(20) else Nil) ++ (if (success2) List(40) else Nil)
            SinkUtils
              .zipParLaw(ZStream.fromIterable(Random.shuffle(stream)), findSink(20), findSink(40))
          }
        },
        suite("zipRight (*>)")(
          test("happy path") {
            assertM(ZStream(1, 2, 3).run(ZSink.head.zipParRight(ZSink.succeed[Int, String]("Hello"))))(
              equalTo(("Hello"))
            )
          }
        ),
        suite("zipWith")(test("happy path") {
          assertM(ZStream(1, 2, 3).run(ZSink.head.zipParLeft(ZSink.succeed[Int, String]("Hello"))))(
            equalTo(Some(1))
          )
        })
      ),
      test("untilOutputZIO") {
        val sink: ZSink[Any, Nothing, Int, Int, Option[Option[Int]]] =
          ZSink.head[Int].untilOutputZIO(h => ZIO.succeed(h.fold(false)(_ >= 10)))
        val assertions = ZIO.foreach(Chunk(1, 3, 7, 20)) { n =>
          assertM(Stream.fromIterable(1 to 100).chunkN(n).run(sink))(equalTo(Some(Some(10))))
        }
        assertions.map(tst => tst.reduce(_ && _))
      },
      suite("flatMap")(
        test("non-empty input") {
          assertM(
            ZStream(1, 2, 3).run(ZSink.head[Int].flatMap((x: Option[Int]) => ZSink.succeed[Int, Option[Int]](x)))
          )(equalTo(Some(1)))
        },
        test("empty input") {
          assertM(ZStream.empty.run(ZSink.head[Int].flatMap(h => ZSink.succeed[Int, Option[Int]](h))))(
            equalTo(None)
          )
        },
        test("with leftovers") {
          val headAndCount: ZSink[Any, Nothing, Int, Int, (Option[Int], Long)] =
            ZSink.head[Int].flatMap(h => ZSink.count.map(cnt => (h, cnt)))
          checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.int)))) { chunks =>
            ZStream.fromChunks(chunks: _*).run(headAndCount).map {
              case (head, count) => {
                assert(head)(equalTo(chunks.flatten.headOption)) &&
                assert(count + head.toList.size)(equalTo(chunks.map(_.size.toLong).sum))
              }
            }
          }
        }
      ),
      test("take")(
        checkM(Gen.chunkOf(Gen.small(Gen.chunkOfN(_)(Gen.int))), Gen.int) { (chunks, n) =>
          ZStream
            .fromChunks(chunks: _*)
            .peel(ZSink.take[Int](n))
            .flatMap { case (chunk, stream) =>
              stream.runCollect.toManaged.map { leftover =>
                assert(chunk)(equalTo(chunks.flatten.take(n))) &&
                assert(leftover)(equalTo(chunks.flatten.drop(n)))
              }
            }
            .useNow
        }
      ),
      test("take emits at end of chunk")(
        Stream(1, 2)
          .concat(Stream.dieMessage("should not get this far"))
          .run(Sink.take(2))
          .map(assert(_)(equalTo(Chunk(1, 2))))
      ),
      test("timed") {
        for {
          f <- ZStream.fromIterable(1 to 10).mapZIO(i => Clock.sleep(10.millis).as(i)).run(ZSink.timed).fork
          _ <- TestClock.adjust(100.millis)
          r <- f.join
        } yield assert(r)(isGreaterThanEqualTo(100.millis))
      }
    )
  )
}
