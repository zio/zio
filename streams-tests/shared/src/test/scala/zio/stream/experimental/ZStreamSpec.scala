package zio.stream.experimental

import zio.ZQueueSpecUtil.waitForSize
import zio._
import zio.stream.ChunkUtils.smallChunks
import zio.stream.experimental.ZStreamUtils.nPulls
import zio.test.Assertion.{ equalTo, isFalse, isTrue }
import zio.test._

object ZStreamSpec extends ZIOBaseSpec {
  def assertLazy(f: (=> Nothing) => Any): UIO[TestResult] =
    UIO.effectTotal {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("ZStreamSpec")(
    suite("Combinators")(
      suite("ensuring")(
        testM("Stream.ensuring") {
          for {
            log <- Ref.make[List[String]](Nil)
            _ <- (for {
                  _ <- ZStream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                  _ <- ZStream.fromEffect(log.update("Use" :: _))
                } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
            execution <- log.get
          } yield assert(execution)(equalTo(List("Ensuring", "Release", "Use", "Acquire")))
        }
      ),
      suite("ensuringFirst")(
        testM("Stream.ensuringFirst") {
          for {
            log <- Ref.make[List[String]](Nil)
            _ <- (for {
                  _ <- ZStream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                  _ <- ZStream.fromEffect(log.update("Use" :: _))
                } yield ()).ensuringFirst(log.update("Ensuring" :: _)).runDrain
            execution <- log.get
          } yield assert(execution)(equalTo(List("Release", "Ensuring", "Use", "Acquire")))
        }
      ),
      suite("flatMap")(
        testM("deep flatMap stack safety") {
          def fib(n: Int): UStream[Int] =
            if (n <= 1) ZStream.succeedNow(n)
            else
              fib(n - 1).flatMap { a =>
                fib(n - 2).flatMap { b =>
                  ZStream.succeedNow(a + b)
                }
              }

          val stream   = fib(20)
          val expected = 6765

          assertM(stream.runCollect)(equalTo(List(expected)))
        }
        //   testM("left identity")(checkM(Gen.anyInt, Gen.function(pureStreamOfInts)) { (x, f) =>
        //     for {
        //       res1 <- ZStream(x).flatMap(f).runCollect
        //       res2 <- f(x).runCollect
        //     } yield assert(res1)(equalTo(res2))
        //   }),
        //   testM("right identity")(
        //     checkM(pureStreamOfInts)(m =>
        //       for {
        //         res1 <- m.flatMap(i => ZStream(i)).runCollect
        //         res2 <- m.runCollect
        //       } yield assert(res1)(equalTo(res2))
        //     )
        //   ),
        //   testM("associativity") {
        //     val tinyStream = Gen.int(0, 2).flatMap(pureStreamGen(Gen.anyInt, _))
        //     val fnGen      = Gen.function(tinyStream)
        //     checkM(tinyStream, fnGen, fnGen) { (m, f, g) =>
        //       for {
        //         leftStream  <- m.flatMap(f).flatMap(g).runCollect
        //         rightStream <- m.flatMap(x => f(x).flatMap(g)).runCollect
        //       } yield assert(leftStream)(equalTo(rightStream))
        //     }
        //   },
        // TODO uncomment when bracket*, tap, ensuring, etc. are migrated
        // testM("inner finalizers") {
        //   for {
        //     effects <- Ref.make(List[Int]())
        //     push    = (i: Int) => effects.update(i :: _)
        //     latch   <- Promise.make[Nothing, Unit]
        //     fiber <- ZStream(
        //               ZStream.bracket(push(1))(_ => push(1)),
        //               ZStream.fromEffect(push(2)),
        //               ZStream.bracket(push(3))(_ => push(3)) *> Stream.fromEffect(
        //                 latch.succeed(()) *> ZIO.never
        //               )
        //             ).flatMap(identity).runDrain.fork
        //     _      <- latch.await
        //     _      <- fiber.interrupt
        //     result <- effects.get
        //   } yield assert(result)(equalTo(List(3, 3, 2, 1, 1)))

        // },
        // testM("finalizer ordering") {
        //   for {
        //     effects <- Ref.make(List[Int]())
        //     push    = (i: Int) => effects.update(i :: _)
        //     stream = for {
        //       _ <- ZStream.bracket(push(1))(_ => push(1))
        //       _ <- ZStream((), ()).tap(_ => push(2)).ensuring(push(2))
        //       _ <- ZStream.bracket(push(3))(_ => push(3))
        //       _ <- ZStream((), ()).tap(_ => push(4)).ensuring(push(4))
        //     } yield ()
        //     _      <- stream.runDrain
        //     result <- effects.get
        //   } yield assert(result)(equalTo(List(1, 2, 3, 4, 4, 4, 3, 2, 3, 4, 4, 4, 3, 2, 1).reverse))
        // },
        // testM("exit signal") {
        //   for {
        //     ref <- Ref.make(false)
        //     inner = ZStream
        //       .bracketExit(UIO.unit)((_, e) =>
        //         e match {
        //           case Exit.Failure(_) => ref.set(true)
        //           case Exit.Success(_) => UIO.unit
        //         }
        //       )
        //       .flatMap(_ => ZStream.failNow("Ouch"))
        //     _   <- ZStream.succeedNow(()).flatMap(_ => inner).runDrain.either.unit
        //     fin <- ref.get
        //   } yield assert(fin)(isTrue)
        // }
      ),
      testM("map") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .map(_.toString)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right("1"), Left(Right(())), Left(Right(()))))))
      },
      testM("filter - keep elements that satisfy the predicate") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .filter(_ > 0)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
      },
      testM("filter - filter out elements that do not satisfy the predicate") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .filter(_ < 0)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Left(Right(())), Left(Right(())), Left(Right(()))))))
      },
      testM("buffer - introduces a buffer between producer/consumer working at different rates") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .buffer(2)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
      }
    ),
    suite("Constructors")(
      suite("bracket")(
        testM("bracket")(
          for {
            done           <- Ref.make(false)
            iteratorStream = ZStream.bracket(UIO(0))(_ => done.set(true))
            result         <- iteratorStream.runCollect
            released       <- done.get
          } yield assert(result)(equalTo(List(0))) && assert(released)(isTrue)
        ),
        testM("bracket short circuits")(
          for {
            done <- Ref.make(false)
            stream = ZStream
              .bracket(UIO(Chunk(0, 1, 2)))(_ => done.set(true))
              .flatMap(ZStream.fromChunk(_))
              .take(2)
            result   <- stream.runCollect
            released <- done.get
          } yield assert(result)(equalTo(List(0, 1))) && assert(released)(isTrue)
        ),
        // testM("no acquisition when short circuiting")(
        //   for {
        //     acquired       <- Ref.make(false)
        //     iteratorStream = (Stream(1) ++ Stream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
        //     _              <- iteratorStream.run(Sink.drain)
        //     result         <- acquired.get
        //   } yield assert(result)(isFalse)
        // ),
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
                          .flatMap(r => ZStream.fromEffect(UIO.succeedNow(r)))
                          .flatMap(r => ZStream.fromEffect(r.get))
                          .runCollect
            rightAssoc <- ZStream
                           .bracket(Ref.make(true))(_.set(false))
                           .flatMap(r => ZStream.fromEffect(UIO(r)).flatMap(r => ZStream.fromEffect(r.get)))
                           .runCollect
          } yield assert(leftAssoc -> rightAssoc)(equalTo(List(true) -> List(true)))
        )
      ),
      suite("fromEffect")(
        testM("success") {
          ZStream
            .fromEffect(UIO.succeed(1))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
        },
        testM("failure") {
          ZStream
            .fromEffect(IO.fail("Ouch"))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))))
        },
        testM("succeedNow")(checkM(Gen.anyInt) { i =>
          ZStream
            .succeedNow(i)
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Right(i), Left(Right(())), Left(Right(()))))))
        })
      ),
      suite("managed")(
        testM("success") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(UIO.succeed(1))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(equalTo(List(Right(1), Left(Right(())), Left(Right(())))))
        },
        testM("acquisition failure") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(IO.fail("Ouch"))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isFalse) && assert(pulls)(
            equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))
          )
        },
        testM("inner failure") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(UIO.succeed(1))(_ => ref.set(true)) *> Managed.fail("Ouch"))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(
            equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))
          )
        }
      ),
      suite("fromIterable")(
        testM("success")(checkM(Gen.listOf(Gen.anyInt)) { (list: List[Int]) =>
          ZStream
            .fromIterable(list)
            .process
            .use(nPulls(_, list.size + 2))
            .map(
              assert(_)(
                equalTo(
                  list.map(Right(_)) ++
                    List(
                      Left(Right(())),
                      Left(Right(()))
                    )
                )
              )
            )
        }),
        testM("lazy") {
          assertLazy(ZStream.fromIterable)
        }
      ),
      suite("fromIterator")(
        testM("success")(checkM(Gen.listOf(Gen.anyInt)) { (list: List[Int]) =>
          ZStream
            .fromIterator(ZIO.succeed(list.iterator))
            .process
            .use(nPulls(_, list.size + 2))
            .map(
              assert(_)(
                equalTo(
                  list.map(Right(_)) ++
                    List(
                      Left(Right(())),
                      Left(Right(()))
                    )
                )
              )
            )
        }),
        testM("failure") {
          ZStream
            .fromIterator(IO.fail("Ouch"))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))))
        }
      )
      // suite("take")(
      // testM("take")(checkM(streamOfBytes, Gen.anyInt) { (s: Stream[String, Byte], n: Int) =>
      //   for {
      //     takeStreamResult <- s.take(n.toLong).runCollect.run
      //     takeListResult   <- s.runCollect.map(_.take(n)).run
      //   } yield assert(takeListResult.succeeded)(isTrue) implies assert(takeStreamResult)(equalTo(takeListResult))
      // }),
      // testM("take short circuits")(
      //   for {
      //     ran    <- Ref.make(false)
      //     stream = (ZStream(1) ++ Stream.fromEffect(ran.set(true)).drain).take(0)
      //     _      <- stream.run(Sink.drain)
      //     result <- ran.get
      //   } yield assert(result)(isFalse)
      // ),
      //  testM("take(0) short circuits")(
      //    for {
      //      units <- Stream.never.take(0).run(Sink.collectAll[Unit])
      //    } yield assert(units)(equalTo(Nil))
      //  ),
      //  testM("take(1) short circuits")(
      //    for {
      //      ints <- (Stream(1) ++ Stream.never).take(1).run(Sink.collectAll[Int])
      //    } yield assert(ints)(equalTo(List(1)))
      //  )
      // )
    ),
    suite("Destructors")(
      testM("toQueue")(checkM(smallChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
        val s = ZStream.fromChunk(c)
        assertM(s.toQueue(1000).use { (queue: Queue[Take[Nothing, Unit, Int]]) =>
          waitForSize(queue, c.length + 1) *> queue.takeAll
        })(equalTo(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End(())))
      })
    )
  )
}
