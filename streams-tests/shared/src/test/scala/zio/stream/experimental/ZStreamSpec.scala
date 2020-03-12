package zio.stream.experimental

import ZStreamGen._

import zio._
import zio.stream.ChunkUtils._
import zio.test.Assertion._
import zio.test._
import zio.ZQueueSpecUtil.waitForSize

object ZStreamSpec extends ZIOBaseSpec {
  def spec = suite("ZStreamSpec")(
    suite("Combinators")(
      suite("bracket")(
        testM("bracket")(
          for {
            done           <- Ref.make(false)
            iteratorStream = ZStream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(ZStream.fromIterable(_))
            result         <- iteratorStream.runCollect
            released       <- done.get
          } yield assert(result)(equalTo(List(0, 1, 2))) && assert(released)(isTrue)
        ),
        testM("bracket short circuits")(
          for {
            done <- Ref.make(false)
            iteratorStream = ZStream
              .bracket(UIO(0 to 3))(_ => done.set(true))
              .flatMap(ZStream.fromIterable(_))
              .take(2)
            result   <- iteratorStream.runCollect
            released <- done.get
          } yield assert(result)(equalTo(List(0, 1))) && assert(released)(isTrue)
        ),
        testM("no acquisition when short circuiting")(
          for {
            acquired       <- Ref.make(false)
            iteratorStream = (ZStream(1) ++ ZStream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
            _              <- iteratorStream.runDrain
            result         <- acquired.get
          } yield assert(result)(isFalse)
        ),
        testM("releases when there are defects") {
          for {
            ref <- Ref.make(false)
            _ <- ZStream
                  .bracket(ZIO.unit)(_ => ref.set(true))
                  .flatMap(_ => ZStream.fromEffect(ZIO.dieMessage("boom")))
                  .runDrain
                  .run
            released <- ref.get
          } yield assert(released)(isTrue)
        },
        testM("flatMap associativity doesn't affect bracket lifetime")(
          for {
            leftAssoc <- ZStream
                          .bracket(Ref.make(true))(_.set(false))
                          .flatMap(ZStream.succeed(_))
                          .flatMap(r => ZStream.fromEffect(r.get))
                          .runCollect
                          .map(_.head)
            rightAssoc <- ZStream
                           .bracket(Ref.make(true))(_.set(false))
                           .flatMap(ZStream.succeed(_).flatMap(r => ZStream.fromEffect(r.get)))
                           .runCollect
                           .map(_.head)
          } yield assert(leftAssoc -> rightAssoc)(equalTo(true -> true))
        )
      ),
      suite("buffer")(
        testM("maintains elements and ordering")(checkM(Gen.listOf(smallChunks(Gen.anyInt))) { list =>
          assertM(
            ZStream.fromChunks(list: _*)
              .buffer(2)
              .runCollect
          )(equalTo(Chunk.fromIterable(list).flatten.toList))
        }),
        testM("buffer the Stream with Error") {
          val e = new RuntimeException("boom")
          assertM(
            (ZStream.range(0, 10) ++ ZStream.fail(e))
              .buffer(2)
              .runCollect
              .run
          )(fails(equalTo(e)))
        },
        testM("fast producer progress independently") {
          for {
            ref   <- Ref.make(List[Int]())
            latch <- Promise.make[Nothing, Unit]
            s     = ZStream.range(1, 5).tap(i => ref.update(i :: _) *> latch.succeed(()).when(i == 4)).buffer(2)
            l <- s.process.use { as =>
                  for {
                    _ <- as
                    _ <- latch.await
                    l <- ref.get
                  } yield l
                }
          } yield assert(l.reverse)(equalTo((1 to 4).toList))
        }
      ),
      suite("flatMap")(
        testM("deep flatMap stack safety") {
          def fib(n: Int): ZStream[Any, Nothing, Int] =
            if (n <= 1) ZStream.succeed(n)
            else
              fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => ZStream.succeed(a + b)))

          val stream   = fib(20)
          val expected = 6765

          assertM(stream.runCollect)(equalTo(List(expected)))
        },
        testM("left identity")(checkM(Gen.anyInt, Gen.function(pureStreamOfInts)) { (x, f) =>
          for {
            res1 <- ZStream(x).flatMap(f).runCollect
            res2 <- f(x).runCollect
          } yield assert(res1)(equalTo(res2))
        }),
        testM("right identity")(
          checkM(pureStreamOfInts)(m =>
            for {
              res1 <- m.flatMap(i => ZStream(i)).runCollect
              res2 <- m.runCollect
            } yield assert(res1)(equalTo(res2))
          )
        ),
        testM("associativity") {
          val tinyStream = Gen.int(0, 2).flatMap(pureStreamGen(Gen.anyInt, _))
          val fnGen      = Gen.function(tinyStream)
          checkM(tinyStream, fnGen, fnGen) { (m, f, g) =>
            for {
              leftStream  <- m.flatMap(f).flatMap(g).runCollect
              rightStream <- m.flatMap(x => f(x).flatMap(g)).runCollect
            } yield assert(leftStream)(equalTo(rightStream))
          }
        },
        testM("inner finalizers") {
          for {
            effects <- Ref.make(List[Int]())
            push    = (i: Int) => effects.update(i :: _)
            latch   <- Promise.make[Nothing, Unit]
            fiber <- ZStream(
                      ZStream.bracket(push(1))(_ => push(1)),
                      ZStream.fromEffect(push(2)),
                      ZStream.bracket(push(3))(_ => push(3)) *> ZStream.fromEffect(
                        latch.succeed(()) *> ZIO.never
                      )
                    ).flatMap(identity).runDrain.fork
            _      <- latch.await
            _      <- fiber.interrupt
            result <- effects.get
          } yield assert(result)(equalTo(List(3, 3, 2, 1, 1)))

        },
        testM("finalizer ordering") {
          for {
            effects <- Ref.make(List[String]())
            push    = (i: String) => effects.update(i :: _)
            stream = for {
              _ <- ZStream.bracket(push("open1"))(_ => push("close1"))
              _ <- ZStream.fromChunks(Chunk(()), Chunk(())).tap(_ => push("use2")).ensuring(push("close2"))
              _ <- ZStream.bracket(push("open3"))(_ => push("close3"))
              _ <- ZStream.fromChunks(Chunk(()), Chunk(())).tap(_ => push("use4")).ensuring(push("close4"))
            } yield ()
            _      <- stream.runDrain
            result <- effects.get
          } yield assert(result.reverse)(
            equalTo(
              List(
                "open1",
                "use2",
                "open3",
                "use4",
                "use4",
                "close4",
                "close3",
                "use2",
                "open3",
                "use4",
                "use4",
                "close4",
                "close3",
                "close2",
                "close1"
              )
            )
          )
        },
        testM("exit signal") {
          for {
            ref <- Ref.make(false)
            inner = ZStream
              .bracketExit(UIO.unit)((_, e) =>
                e match {
                  case Exit.Failure(_) => ref.set(true)
                  case Exit.Success(_) => UIO.unit
                }
              )
              .flatMap(_ => ZStream.fail("Ouch"))
            _   <- ZStream.succeed(()).flatMap(_ => inner).runDrain.either.unit
            fin <- ref.get
          } yield assert(fin)(isTrue)
        }
      ),
      suite("mergeWith")(
        testM("equivalence with set union")(checkM(streamOfInts, streamOfInts) {
          (s1: ZStream[Any, String, Int], s2: ZStream[Any, String, Int]) =>
            for {
              mergedStream <- (s1 merge s2).runCollect.map(_.toSet).run
              mergedLists <- s1.runCollect
                              .zipWith(s2.runCollect)((left, right) => left ++ right)
                              .map(_.toSet)
                              .run
            } yield assert(!mergedStream.succeeded && !mergedLists.succeeded)(isTrue) || assert(mergedStream)(
              equalTo(mergedLists)
            )
        }),
        testM("prioritizes failure") {
          val s1 = ZStream.never
          val s2 = ZStream.fail("Ouch")

          assertM(s1.mergeWith(s2)(_ => (), _ => ()).runCollect.either)(isLeft(equalTo("Ouch")))
        }
      ),
      suite("toQueue")(
        testM("toQueue")(checkM(smallChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
          val s = ZStream.fromChunk(c).flatMap(ZStream.succeed(_))
          assertM(s.toQueue(1000).use(queue => waitForSize(queue, c.length + 1) *> queue.takeAll))(
            equalTo(c.toSeq.toList.map(i => Exit.succeed(Chunk(i))) :+ Exit.fail(None))
          )
        }),
        testM("toQueueUnbounded")(checkM(smallChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
          val s = ZStream.fromChunk(c).flatMap(ZStream.succeed(_))
          assertM(s.toQueueUnbounded.use(queue => waitForSize(queue, c.length + 1) *> queue.takeAll))(
            equalTo(c.toSeq.toList.map(i => Exit.succeed(Chunk(i))) :+ Exit.fail(None))
          )
        })
      ),
      suite("zipWith")(
        testM("zip equivalence with Chunk#zipWith") {
          checkM(
            // We're using ZStream.fromChunks in the test, and that discards empty
            // chunks; so we're only testing for non-empty chunks here.
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0)),
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0))
          ) {
            (l, r) =>
              // zipWith pulls one last time after the last chunk,
              // so we take the smaller side + 1.
              val expected =
                if (l.size <= r.size)
                  Chunk.fromIterable(l).flatten.zipWith(Chunk.fromIterable(r.take(l.size + 1)).flatten)((_, _))
                else Chunk.fromIterable(l.take(r.size + 1)).flatten.zipWith(Chunk.fromIterable(r).flatten)((_, _))

              assertM(ZStream.fromChunks(l: _*).zip(ZStream.fromChunks(r: _*)).runCollect)(equalTo(expected.toList))
          }
        },
        testM("zipWith prioritizes failure") {
          assertM(
            ZStream.never
              .zipWith(ZStream.fail("Ouch"))((_, _) => None)
              .runCollect
              .either
          )(isLeft(equalTo("Ouch")))
        }
      ),
      suite("zipAllWith")(
        testM("zipAllWith") {
          checkM(
            // We're using ZStream.fromChunks in the test, and that discards empty
            // chunks; so we're only testing for non-empty chunks here.
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0)),
            Gen.listOf(smallChunks(Gen.anyInt).filter(_.size > 0))
          ) { (l, r) =>
            val expected =
              Chunk
                .fromIterable(l)
                .flatten
                .zipAllWith(Chunk.fromIterable(r).flatten)(Some(_) -> None, None -> Some(_))(
                  Some(_) -> Some(_)
                )

            assertM(
              ZStream
                .fromChunks(l: _*)
                .map(Option(_))
                .zipAll(ZStream.fromChunks(r: _*).map(Option(_)))(None, None)
                .runCollect
            )(equalTo(expected.toList))
          }
        },
        testM("zipAllWith prioritizes failure") {
          assertM(
            ZStream.never
              .zipAll(ZStream.fail("Ouch"))(None, None)
              .runCollect
              .either
          )(isLeft(equalTo("Ouch")))
        }
      ),
      testM("zipWithIndex")(checkM(pureStreamOfBytes) { s =>
        for {
          res1 <- (s.zipWithIndex.runCollect)
          res2 <- (s.runCollect.map(_.zipWithIndex.map(t => (t._1, t._2.toLong))))
        } yield assert(res1)(equalTo(res2))
      })
    ),
    suite("Constructors")(
      testM("fromChunk") {
        checkM(smallChunks(Gen.anyInt))(c => assertM(ZStream.fromChunk(c).runCollect)(equalTo(c.toList)))
      },
      suite("fromChunks")(
        testM("fromChunks") {
          checkM(Gen.listOf(smallChunks(Gen.anyInt))) { cs =>
            assertM(ZStream.fromChunks(cs: _*).runCollect)(equalTo(Chunk.fromIterable(cs).flatten.toList))
          }
        },
        testM("discards empty chunks") {
          ZStream.fromChunks(Chunk(1), Chunk.empty, Chunk(1)).process.use { pull =>
            assertM(nPulls(pull, 3))(equalTo(List(Right(Chunk(1)), Right(Chunk(1)), Left(None))))
          }
        }
      ),
      testM("concatAll") {
        checkM(Gen.listOf(smallChunks(Gen.anyInt))) { chunks =>
          assertM(ZStream.concatAll(Chunk.fromIterable(chunks.map(ZStream.fromChunk(_)))).runCollect)(
            equalTo(Chunk.fromIterable(chunks).flatten.toList)
          )
        }
      }
    )
  )
}
