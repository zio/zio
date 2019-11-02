package zio.stream

import zio._
import zio.ZQueueSpecUtil.waitForSize
import zio.clock.Clock
import zio.duration._
import zio.test._
import zio.test.Assertion.{
  dies,
  equalTo,
  fails,
  hasSameElements,
  isFalse,
  isGreaterThan,
  isLeft,
  isRight,
  isTrue,
  isUnit
}
import scala.{ Stream => _ }
import Exit.Success
import StreamUtils._

object StreamSpec
    extends ZIOBaseSpec(
      suite("StreamSpec")(
        suite("Stream.aggregate")(
          testM("aggregate") {
            Stream(1, 1, 1, 1)
              .aggregate(ZSink.foldUntil(List[Int](), 3)((acc, el: Int) => el :: acc).map(_.reverse))
              .runCollect
              .map { result =>
                assert(result.flatten, equalTo(List(1, 1, 1, 1))) &&
                assert(result.forall(_.length <= 3), isTrue)
              }
          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")
            assertM(
              Stream(1, 1, 1, 1)
                .aggregate(ZSink.die(e))
                .runCollect
                .run,
              dies(equalTo(e))
            )
          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")
            val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]())(_ => true) { (_, _) =>
              ZIO.die(e)
            }

            assertM(
              Stream(1, 1)
                .aggregate(sink)
                .runCollect
                .run,
              dies(equalTo(e))
            )
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
                if (el == 1) UIO.succeed((el :: acc, Chunk[Int]()))
                else
                  (latch.succeed(()) *> UIO.never)
                    .onInterrupt(cancelled.set(true))
              }
              fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.fromEffect {
                (latch.succeed(()) *> UIO.never)
                  .onInterrupt(cancelled.set(true))
              }
              fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)
          },
          testM("leftover handling") {
            val data = List(1, 2, 2, 3, 2, 3)
            assertM(
              Stream(data: _*)
                .aggregate(
                  Sink
                    .foldWeighted(List[Int]())((i: Int) => i.toLong, 4) { (acc, el) =>
                      el :: acc
                    }
                    .map(_.reverse)
                )
                .runCollect
                .map(_.flatten),
              equalTo(data)
            )
          }
        ),
        suite("Stream.aggregateWithinEither")(
          testM("aggregateWithinEither") {
            for {
              result <- Stream(1, 1, 1, 1, 2)
                         .aggregateWithinEither(
                           Sink
                             .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                               if (el == 1) ((el :: acc._1, true), Chunk.empty)
                               else if (el == 2 && acc._1.isEmpty) ((el :: acc._1, false), Chunk.empty)
                               else ((acc._1, false), Chunk.single(el))
                             }
                             .map(_._1),
                           ZSchedule.spaced(30.minutes)
                         )
                         .runCollect
            } yield assert(result, equalTo(List(Right(List(1, 1, 1, 1)), Right(List(2)))))
          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")
            assertM(
              Stream(1, 1, 1, 1)
                .aggregateWithinEither(ZSink.die(e), ZSchedule.spaced(30.minutes))
                .runCollect
                .run,
              dies(equalTo(e))
            )
          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")
            val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]())(_ => true) { (_, _) =>
              ZIO.die(e)
            }

            assertM(
              Stream(1, 1)
                .aggregateWithinEither(sink, ZSchedule.spaced(30.minutes))
                .runCollect
                .run,
              dies(equalTo(e))
            )
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
                if (el == 1) UIO.succeed((el :: acc, Chunk[Int]()))
                else
                  (latch.succeed(()) *> UIO.never)
                    .onInterrupt(cancelled.set(true))
              }
              fiber <- Stream(1, 1, 2)
                        .aggregateWithinEither(sink, ZSchedule.spaced(30.minutes))
                        .runCollect
                        .untraced
                        .fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = Sink.fromEffect {
                (latch.succeed(()) *> UIO.never)
                  .onInterrupt(cancelled.set(true))
              }
              fiber <- Stream(1, 1, 2)
                        .aggregateWithinEither(sink, ZSchedule.spaced(30.minutes))
                        .runCollect
                        .untraced
                        .fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result, isTrue)
          },
          testM("aggregateWithinEitherLeftoverHandling") {
            val data = List(1, 2, 2, 3, 2, 3)
            assertM(
              Stream(data: _*)
                .aggregateWithinEither(
                  Sink
                    .foldWeighted(List[Int]())((i: Int) => i.toLong, 4) { (acc, el) =>
                      el :: acc
                    }
                    .map(_.reverse),
                  ZSchedule.spaced(100.millis)
                )
                .collect {
                  case Right(v) => v
                }
                .runCollect
                .map(_.flatten),
              equalTo(data)
            )
          }
        ),
        suite("Stream.aggregateWithin")(
          testM("aggregateWithin") {
            for {
              result <- Stream(1, 1, 1, 1, 2)
                         .aggregateWithin(
                           Sink
                             .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                               if (el == 1) ((el :: acc._1, true), Chunk.empty)
                               else if (el == 2 && acc._1.isEmpty) ((el :: acc._1, false), Chunk.empty)
                               else ((acc._1, false), Chunk.single(el))
                             }
                             .map(_._1),
                           ZSchedule.spaced(30.minutes)
                         )
                         .runCollect
            } yield assert(result, equalTo(List(List(1, 1, 1, 1), List(2))))
          }
        ),
        suite("Stream.bracket")(
          testM("bracket")(
            for {
              done           <- Ref.make(false)
              iteratorStream = Stream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(Stream.fromIterable)
              result         <- iteratorStream.run(Sink.collectAll[Int])
              released       <- done.get
            } yield assert(result, equalTo(List(0, 1, 2))) && assert(released, isTrue)
          ),
          testM("bracket short circuits")(
            for {
              done <- Ref.make(false)
              iteratorStream = Stream
                .bracket(UIO(0 to 3))(_ => done.set(true))
                .flatMap(Stream.fromIterable)
                .take(2)
              result   <- iteratorStream.run(Sink.collectAll[Int])
              released <- done.get
            } yield assert(result, equalTo(List(0, 1))) && assert(released, isTrue)
          ),
          testM("no acquisition when short circuiting")(
            for {
              acquired       <- Ref.make(false)
              iteratorStream = (Stream(1) ++ Stream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
              _              <- iteratorStream.run(Sink.drain)
              result         <- acquired.get
            } yield assert(result, isFalse)
          ),
          testM("releases when there are defects") {
            for {
              ref <- Ref.make(false)
              _ <- Stream
                    .bracket(ZIO.unit)(_ => ref.set(true))
                    .flatMap(_ => Stream.fromEffect(ZIO.dieMessage("boom")))
                    .run(Sink.drain)
                    .run
              released <- ref.get
            } yield assert(released, isTrue)
          }
        ),
        suite("Stream.broadcast")(
          testM("Values") {
            Stream
              .range(0, 5)
              .broadcast(2, 12)
              .use {
                case s1 :: s2 :: Nil =>
                  for {
                    out1     <- s1.runCollect
                    out2     <- s2.runCollect
                    expected = List(0, 1, 2, 3, 4, 5)
                  } yield assert(out1, equalTo(expected)) && assert(out2, equalTo(expected))
                case _ =>
                  UIO(assert((), Assertion.nothing))
              }
          },
          testM("Errors") {
            (Stream.range(0, 1) ++ Stream.fail("Boom")).broadcast(2, 12).use {
              case s1 :: s2 :: Nil =>
                for {
                  out1     <- s1.runCollect.either
                  out2     <- s2.runCollect.either
                  expected = Left("Boom")
                } yield assert(out1, equalTo(expected)) && assert(out2, equalTo(expected))
              case _ =>
                UIO(assert((), Assertion.nothing))
            }
          },
          testM("BackPressure") {
            Stream
              .range(0, 5)
              .broadcast(2, 2)
              .use {
                case s1 :: s2 :: Nil =>
                  for {
                    ref       <- Ref.make[List[Int]](Nil)
                    latch1    <- Promise.make[Nothing, Unit]
                    fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
                    _         <- latch1.await
                    snapshot1 <- ref.get
                    _         <- s2.runDrain
                    _         <- fib.await
                    snapshot2 <- ref.get
                  } yield assert(snapshot1, equalTo(List(2, 1, 0))) && assert(
                    snapshot2,
                    equalTo(List(5, 4, 3, 2, 1, 0))
                  )
                case _ =>
                  UIO(assert((), Assertion.nothing))
              }
          },
          testM("Unsubscribe") {
            Stream.range(0, 5).broadcast(2, 2).use {
              case s1 :: s2 :: Nil =>
                for {
                  _    <- s1.process.use_(ZIO.unit).ignore
                  out2 <- s2.runCollect
                } yield assert(out2, equalTo(List(0, 1, 2, 3, 4, 5)))
              case _ =>
                UIO(assert((), Assertion.nothing))
            }
          }
        ),
        suite("Stream.catchAllCause")(
          testM("recovery from errors") {
            val s1 = Stream(1, 2) ++ Stream.fail("Boom")
            val s2 = Stream(3, 4)
            s1.catchAllCause(_ => s2).runCollect.map(assert(_, equalTo(List(1, 2, 3, 4))))
          },
          testM("recovery from defects") {
            val s1 = Stream(1, 2) ++ Stream.dieMessage("Boom")
            val s2 = Stream(3, 4)
            s1.catchAllCause(_ => s2).runCollect.map(assert(_, equalTo(List(1, 2, 3, 4))))
          },
          testM("happy path") {
            val s1 = Stream(1, 2)
            val s2 = Stream(3, 4)
            s1.catchAllCause(_ => s2).runCollect.map(assert(_, equalTo(List(1, 2))))
          },
          testM("executes finalizers") {
            for {
              fins   <- Ref.make(List[String]())
              s1     = (Stream(1, 2) ++ Stream.fail("Boom")).ensuring(fins.update("s1" :: _))
              s2     = (Stream(3, 4) ++ Stream.fail("Boom")).ensuring(fins.update("s2" :: _))
              _      <- s1.catchAllCause(_ => s2).runCollect.run
              result <- fins.get
            } yield assert(result, equalTo(List("s2", "s1")))
          },
          testM("failures on the scope") {
            val s1 = Stream(1, 2) ++ ZStream(ZManaged.fail("Boom"))
            val s2 = Stream(3, 4)
            s1.catchAllCause(_ => s2).runCollect.map(assert(_, equalTo(List(1, 2, 3, 4))))
          }
        ),
        suite("Stream.chunkN")(
          testM("empty stream") {
            assertM(Stream.empty.chunkN(1).runCollect, equalTo(Nil))
          },
          testM("non-positive chunk size") {
            assertM(Stream(1, 2, 3).chunkN(0).runCollect, equalTo(List(Chunk(1), Chunk(2), Chunk(3))))
          },
          testM("full last chunk") {
            assertM(
              Stream(1, 2, 3, 4, 5, 6).chunkN(2).runCollect,
              equalTo(List(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6)))
            )
          },
          testM("non-full last chunk") {
            assertM(Stream(1, 2, 3, 4, 5).chunkN(2).runCollect, equalTo(List(Chunk(1, 2), Chunk(3, 4), Chunk(5))))
          },
          testM("error") {
            (Stream(1, 2, 3, 4, 5) ++ Stream.fail("broken"))
              .chunkN(3)
              .catchAll(_ => Stream(Chunk(6)))
              .runCollect
              .map(assert(_, equalTo(List(Chunk(1, 2, 3), Chunk(4, 5), Chunk(6)))))
          }
        ),
        testM("Stream.collect") {
          assertM(Stream(Left(1), Right(2), Left(3)).collect {
            case Right(n) => n
          }.runCollect, equalTo(List(2)))
        },
        suite("Stream.collectWhile")(
          testM("collectWhile") {
            assertM(
              Stream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile { case Some(v) => v }.runCollect,
              equalTo(List(1, 2, 3))
            )
          },
          testM("collectWhile short circuits") {
            assertM((Stream(Option(1)) ++ Stream.fail("Ouch")).collectWhile {
              case None => 1
            }.runDrain.either, isRight(isUnit))
          }
        ),
        suite("Stream.concat")(
          testM("concat")(checkM(streamOfBytes, streamOfBytes) { (s1, s2) =>
            for {
              listConcat   <- s1.runCollect.zipWith(s2.runCollect)(_ ++ _).run
              streamConcat <- (s1 ++ s2).runCollect.run
            } yield assert(streamConcat.succeeded && listConcat.succeeded, isTrue) implies assert(
              streamConcat,
              equalTo(listConcat)
            )
          }),
          testM("finalizer order") {
            for {
              log <- Ref.make[List[String]](Nil)
              _ <- (Stream.finalizer(log.update("Second" :: _)) ++ Stream
                    .finalizer(log.update("First" :: _))).runDrain
              execution <- log.get
            } yield assert(execution, equalTo(List("First", "Second")))
          }
        ),
        testM("Stream.drain")(
          for {
            ref <- Ref.make(List[Int]())
            _   <- Stream.range(0, 10).mapM(i => ref.update(i :: _)).drain.run(Sink.drain)
            l   <- ref.get
          } yield assert(l.reverse, equalTo((0 to 10).toList))
        ),
        suite("Stream.dropUntil")(testM("dropUntil") {
          checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
            for {
              res1 <- s.dropUntil(p).runCollect
              res2 <- s.runCollect.map(StreamUtils.dropUntil(_)(p))
            } yield assert(res1, equalTo(res2))
          }
        }),
        suite("Stream.dropWhile")(
          testM("dropWhile")(
            checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s: Stream[String, Byte], p: Byte => Boolean) =>
              for {
                res1 <- s.dropWhile(p).runCollect
                res2 <- s.runCollect.map(_.dropWhile(p))
              } yield assert(res1, equalTo(res2))
            }
          ),
          testM("short circuits") {
            assertM(
              (Stream(1) ++ Stream.fail("Ouch"))
                .take(1)
                .dropWhile(_ => true)
                .runDrain
                .either,
              isRight(isUnit)
            )
          }
        ),
        testM("Stream.either") {
          val s = Stream(1, 2, 3) ++ Stream.fail("Boom")
          s.either.runCollect.map(assert(_, equalTo(List(Right(1), Right(2), Right(3), Left("Boom")))))
        },
        testM("Stream.ensuring") {
          for {
            log <- Ref.make[List[String]](Nil)
            _ <- (for {
                  _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                  _ <- Stream.fromEffect(log.update("Use" :: _))
                } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
            execution <- log.get
          } yield assert(execution, equalTo(List("Ensuring", "Release", "Use", "Acquire")))
        },
        testM("Stream.ensuringFirst") {
          for {
            log <- Ref.make[List[String]](Nil)
            _ <- (for {
                  _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                  _ <- Stream.fromEffect(log.update("Use" :: _))
                } yield ()).ensuringFirst(log.update("Ensuring" :: _)).runDrain
            execution <- log.get
          } yield assert(execution, equalTo(List("Release", "Ensuring", "Use", "Acquire")))
        },
        testM("Stream.finalizer") {
          for {
            log <- Ref.make[List[String]](Nil)
            _ <- (for {
                  _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                  _ <- Stream.finalizer(log.update("Use" :: _))
                } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
            execution <- log.get
          } yield assert(execution, equalTo(List("Ensuring", "Release", "Use", "Acquire")))
        },
        testM("Stream.filter")(checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
          for {
            res1 <- s.filter(p).runCollect
            res2 <- s.runCollect.map(_.filter(p))
          } yield assert(res1, equalTo(res2))
        }),
        testM("Stream.filterM")(checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
          for {
            res1 <- s.filterM(s => IO.succeed(p(s))).runCollect
            res2 <- s.runCollect.map(_.filter(p))
          } yield assert(res1, equalTo(res2))
        }),
        suite("Stream.flatMap")(
          testM("deep flatMap stack safety") {
            def fib(n: Int): Stream[Nothing, Int] =
              if (n <= 1) Stream.succeed(n)
              else
                fib(n - 1).flatMap { a =>
                  fib(n - 2).flatMap { b =>
                    Stream.succeed(a + b)
                  }
                }

            val stream   = fib(20)
            val expected = 6765

            assertM(stream.runCollect, equalTo(List(expected)))
          },
          testM("left identity")(checkM(Gen.anyInt, Gen.function(pureStreamOfInts)) { (x, f) =>
            for {
              res1 <- Stream(x).flatMap(f).runCollect
              res2 <- f(x).runCollect
            } yield assert(res1, equalTo(res2))
          }),
          testM("right identity")(
            checkM(pureStreamOfInts)(
              m =>
                for {
                  res1 <- m.flatMap(i => Stream(i)).runCollect
                  res2 <- m.runCollect
                } yield assert(res1, equalTo(res2))
            )
          ),
          testM("associativity") {
            val tinyStream = Gen.int(0, 2).flatMap(pureStreamGen(Gen.anyInt, _))
            val fnGen      = Gen.function(tinyStream)
            checkM(tinyStream, fnGen, fnGen) { (m, f, g) =>
              for {
                leftStream  <- m.flatMap(f).flatMap(g).runCollect
                rightStream <- m.flatMap(x => f(x).flatMap(g)).runCollect
              } yield assert(leftStream, equalTo(rightStream))
            }
          },
          testM("inner finalizers") {
            for {
              effects <- Ref.make(List[Int]())
              push    = (i: Int) => effects.update(i :: _)
              latch   <- Promise.make[Nothing, Unit]
              fiber <- Stream(
                        Stream.bracket(push(1))(_ => push(1)),
                        Stream.fromEffect(push(2)),
                        Stream.bracket(push(3))(_ => push(3)) *> Stream.fromEffect(
                          latch.succeed(()) *> ZIO.never
                        )
                      ).flatMap(identity).runDrain.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- effects.get
            } yield assert(result, equalTo(List(3, 3, 2, 1, 1)))

          },
          testM("finalizer ordering") {
            for {
              effects <- Ref.make(List[Int]())
              push    = (i: Int) => effects.update(i :: _)
              stream = for {
                _ <- Stream.bracket(push(1))(_ => push(1))
                _ <- Stream((), ()).tap(_ => push(2)).ensuring(push(2))
                _ <- Stream.bracket(push(3))(_ => push(3))
                _ <- Stream((), ()).tap(_ => push(4)).ensuring(push(4))
              } yield ()
              _      <- stream.runDrain
              result <- effects.get
            } yield assert(result, equalTo(List(1, 2, 3, 4, 4, 4, 3, 2, 3, 4, 4, 4, 3, 2, 1).reverse))
          }
        ),
        suite("Stream.flatMapPar / flattenPar / mergeAll")(
          testM("guarantee ordering")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { m: List[Int] =>
            for {
              flatMap    <- Stream.fromIterable(m).flatMap(i => Stream(i, i)).runCollect
              flatMapPar <- Stream.fromIterable(m).flatMapPar(1)(i => Stream(i, i)).runCollect
            } yield assert(flatMap, equalTo(flatMapPar))
          }),
          testM("consistent with flatMap")(checkM(Gen.int(1, Int.MaxValue), Gen.small(Gen.listOfN(_)(Gen.anyInt))) {
            (n, m) =>
              for {
                flatMap    <- Stream.fromIterable(m).flatMap(i => Stream(i, i)).runCollect.map(_.toSet)
                flatMapPar <- Stream.fromIterable(m).flatMapPar(n)(i => Stream(i, i)).runCollect.map(_.toSet)
              } yield assert(n, isGreaterThan(0)) implies assert(flatMap, equalTo(flatMapPar))
          }),
          testM("short circuiting") {
            assertM(
              Stream
                .mergeAll(2)(
                  Stream.never,
                  Stream(1)
                )
                .take(1)
                .run(Sink.collectAll[Int]),
              equalTo(List(1))
            )
          },
          testM("interruption propagation") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              fiber <- Stream(())
                        .flatMapPar(1)(
                          _ =>
                            Stream.fromEffect(
                              (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
                            )
                        )
                        .run(Sink.collectAll[Unit])
                        .fork
              _         <- latch.await
              _         <- fiber.interrupt
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue)
          },
          testM("inner errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- Stream(
                         Stream.fromEffect(
                           (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
                         ),
                         Stream.fromEffect(latch.await *> ZIO.fail("Ouch"))
                       ).flatMapPar(2)(identity)
                         .run(Sink.drain)
                         .either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))
          },
          testM("outer errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.fail("Ouch")))
                         .flatMapPar(2) { _ =>
                           Stream.fromEffect(
                             (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .run(Sink.drain)
                         .either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))
          },
          testM("inner defects interrupt all fibers") {
            val ex = new RuntimeException("Ouch")

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- Stream(
                         Stream.fromEffect(
                           (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
                         ),
                         Stream.fromEffect(latch.await *> ZIO.die(ex))
                       ).flatMapPar(2)(identity)
                         .run(Sink.drain)
                         .run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, dies(equalTo(ex)))
          },
          testM("outer defects interrupt all fibers") {
            val ex = new RuntimeException()

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.die(ex)))
                         .flatMapPar(2) { _ =>
                           Stream.fromEffect(
                             (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .run(Sink.drain)
                         .run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, dies(equalTo(ex)))
          },
          testM("finalizer ordering") {
            for {
              execution <- Ref.make[List[String]](Nil)
              inner = Stream
                .bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
              _ <- Stream
                    .bracket(execution.update("OuterAcquire" :: _).as(inner))(
                      _ => execution.update("OuterRelease" :: _)
                    )
                    .flatMapPar(2)(identity)
                    .runDrain
              results <- execution.get
            } yield assert(results, equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
          }
        ),
        suite("Stream.flatMapParSwitch")(
          testM("guarantee ordering no parallelism") {
            for {
              lastExecuted <- Ref.make(false)
              semaphore    <- Semaphore.make(1)
              _ <- Stream(1, 2, 3, 4)
                    .flatMapParSwitch(1) { i =>
                      if (i > 3) Stream.bracket(UIO.unit)(_ => lastExecuted.set(true)).flatMap(_ => Stream.empty)
                      else Stream.bracket(semaphore.acquire)(_ => semaphore.release).flatMap(_ => Stream.never)
                    }
                    .runDrain
              result <- semaphore.withPermit(lastExecuted.get)
            } yield assert(result, isTrue)
          },
          testM("guarantee ordering with parallelism") {
            for {
              lastExecuted <- Ref.make(0)
              semaphore    <- Semaphore.make(4)
              _ <- Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                    .flatMapParSwitch(4) { i =>
                      if (i > 8)
                        Stream.bracket(UIO.unit)(_ => lastExecuted.update(_ + 1)).flatMap(_ => Stream.empty)
                      else Stream.bracket(semaphore.acquire)(_ => semaphore.release).flatMap(_ => Stream.never)
                    }
                    .runDrain
              result <- semaphore.withPermits(4)(lastExecuted.get)
            } yield assert(result, equalTo(4))
          },
          testM("short circuiting") {
            assertM(
              Stream(Stream.never, Stream(1))
                .flatMapParSwitch(2)(identity)
                .take(1)
                .runCollect,
              equalTo(List(1))
            )
          },
          testM("interruption propagation") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              fiber <- Stream(())
                        .flatMapParSwitch(1)(
                          _ =>
                            Stream.fromEffect(
                              (latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true))
                            )
                        )
                        .runCollect
                        .fork
              _         <- latch.await
              _         <- fiber.interrupt
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue)
          },
          testM("inner errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- Stream(
                         Stream.fromEffect(
                           (latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true))
                         ),
                         Stream.fromEffect(latch.await *> IO.fail("Ouch"))
                       ).flatMapParSwitch(2)(identity).runDrain.either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))
          },
          testM("outer errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (Stream(()) ++ Stream.fromEffect(latch.await *> IO.fail("Ouch")))
                         .flatMapParSwitch(2) { _ =>
                           Stream.fromEffect(
                             (latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .runDrain
                         .either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, isLeft(equalTo("Ouch")))
          },
          testM("inner defects interrupt all fibers") {
            val ex = new RuntimeException("Ouch")

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- Stream(
                         Stream.fromEffect(
                           (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
                         ),
                         Stream.fromEffect(latch.await *> ZIO.die(ex))
                       ).flatMapParSwitch(2)(identity)
                         .run(Sink.drain)
                         .run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, dies(equalTo(ex)))
          },
          testM("outer defects interrupt all fibers") {
            val ex = new RuntimeException()

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.die(ex)))
                         .flatMapParSwitch(2) { _ =>
                           Stream.fromEffect(
                             (latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .run(Sink.drain)
                         .run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled, isTrue) && assert(result, dies(equalTo(ex)))
          },
          testM("finalizer ordering") {
            for {
              execution <- Ref.make(List.empty[String])
              inner = Stream.bracket(execution.update("InnerAcquire" :: _))(
                _ => execution.update("InnerRelease" :: _)
              )
              _ <- Stream
                    .bracket(execution.update("OuterAcquire" :: _).as(inner))(
                      _ => execution.update("OuterRelease" :: _)
                    )
                    .flatMapParSwitch(2)(identity)
                    .runDrain
              results <- execution.get
            } yield assert(results, equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
          }
        ),
        suite("Stream.foreach/foreachWhile")(
          testM("foreach") {
            for {
              ref <- Ref.make(0)
              _   <- Stream(1, 1, 1, 1, 1).foreach[Any, Nothing](a => ref.update(_ + a))
              sum <- ref.get
            } yield assert(sum, equalTo(5))
          },
          testM("foreachWhile") {
            for {
              ref <- Ref.make(0)
              _ <- Stream(1, 1, 1, 1, 1, 1).foreachWhile[Any, Nothing](
                    a =>
                      ref.modify(
                        sum =>
                          if (sum >= 3) (false, sum)
                          else (true, sum + a)
                      )
                  )
              sum <- ref.get
            } yield assert(sum, equalTo(3))
          },
          testM("foreachWhile short circuits") {
            for {
              flag    <- Ref.make(true)
              _       <- (Stream(true, true, false) ++ Stream.fromEffect(flag.set(false)).drain).foreachWhile(ZIO.succeed)
              skipped <- flag.get
            } yield assert(skipped, isTrue)
          }
        ),
        testM("Stream.forever") {
          for {
            ref <- Ref.make(0)
            _ <- Stream(1).forever.foreachWhile[Any, Nothing](
                  _ => ref.modify(sum => (if (sum >= 9) false else true, sum + 1))
                )
            sum <- ref.get
          } yield assert(sum, equalTo(10))
        },
        testM("Stream.fromChunk")(checkM(smallChunks(Gen.anyInt)) { c =>
          assertM(Stream.fromChunk(c).runCollect, equalTo(c.toSeq.toList))
        }),
        testM("Stream.fromInputStream") {
          import java.io.ByteArrayInputStream
          val chunkSize = ZStreamChunk.DefaultChunkSize
          val data      = Array.tabulate[Byte](chunkSize * 5 / 2)(_.toByte)
          val is        = new ByteArrayInputStream(data)
          ZStream.fromInputStream(is, chunkSize).run(Sink.collectAll[Chunk[Byte]]) map { chunks =>
            assert(chunks.flatMap(_.toArray[Byte]).toArray, equalTo(data))
          }
        },
        testM("Stream.fromIterable")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
          assertM(Stream.fromIterable(l).runCollect, equalTo(l))
        }),
        testM("Stream.fromIterator")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
          assertM(Stream.fromIterator(UIO.effectTotal(l.iterator)).runCollect, equalTo(l))
        }),
        testM("Stream.fromQueue")(checkM(smallChunks(Gen.anyInt)) { c =>
          for {
            queue <- Queue.unbounded[Int]
            _     <- queue.offerAll(c.toSeq)
            fiber <- Stream
                      .fromQueue(queue)
                      .foldWhileM[Any, Nothing, Int, List[Int]](List[Int]())(_ => true)(
                        (acc, el) => IO.succeed(el :: acc)
                      )
                      .map(_.reverse)
                      .fork
            _     <- waitForSize(queue, -1)
            _     <- queue.shutdown
            items <- fiber.join
          } yield assert(items, equalTo(c.toSeq.toList))
        }),
        suite("Stream.groupBy")(
          testM("values") {
            val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
            assertM(
              Stream
                .fromIterable(words)
                .groupByKey(identity, 8192) {
                  case (k, s) =>
                    s.transduce(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
                      .take(1)
                      .map((k -> _))
                }
                .runCollect
                .map(_.toMap),
              equalTo((0 to 100).map((_.toString -> 1000)).toMap)
            )
          },
          testM("first") {
            val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
            assertM(
              Stream
                .fromIterable(words)
                .groupByKey(identity, 1050)
                .first(2) {
                  case (k, s) =>
                    s.transduce(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
                      .take(1)
                      .map((k -> _))
                }
                .runCollect
                .map(_.toMap),
              equalTo((0 to 1).map((_.toString -> 1000)).toMap)
            )
          },
          testM("filter") {
            val words = List.fill(1000)(0 to 100).flatten
            assertM(
              Stream
                .fromIterable(words)
                .groupByKey(identity, 1050)
                .filter(_ <= 5) {
                  case (k, s) =>
                    s.transduce(Sink.foldLeft[Int, Int](0) { case (acc, _) => acc + 1 })
                      .take(1)
                      .map((k -> _))
                }
                .runCollect
                .map(_.toMap),
              equalTo((0 to 5).map((_ -> 1000)).toMap)
            )
          },
          testM("outer errors") {
            val words = List("abc", "test", "test", "foo")
            assertM(
              (Stream.fromIterable(words) ++ Stream.fail("Boom"))
                .groupByKey(identity) { case (_, s) => s.drain }
                .runCollect
                .either,
              isLeft(equalTo("Boom"))
            )
          }
        ),
        testM("Stream.grouped")(
          assertM(
            Stream(1, 2, 3, 4).grouped(2).run(ZSink.collectAll[List[Int]]),
            equalTo(List(List(1, 2), List(3, 4)))
          )
        ),
        suite("Stream interleaving")(
          testM("interleave") {
            val s1 = Stream(2, 3)
            val s2 = Stream(5, 6, 7)

            assertM(s1.interleave(s2).runCollect, equalTo(List(2, 5, 3, 6, 7)))
          },
          testM("interleaveWith") {
            def interleave(b: List[Boolean], s1: => List[Int], s2: => List[Int]): List[Int] =
              b.headOption.map { hd =>
                if (hd) s1 match {
                  case h :: t =>
                    h :: interleave(b.tail, t, s2)
                  case _ =>
                    if (s2.isEmpty) List.empty
                    else interleave(b.tail, List.empty, s2)
                } else
                  s2 match {
                    case h :: t =>
                      h :: interleave(b.tail, s1, t)
                    case _ =>
                      if (s1.isEmpty) List.empty
                      else interleave(b.tail, s1, List.empty)
                  }
              }.getOrElse(List.empty)

            val int = Gen.int(0, 5)

            checkM(
              int.flatMap(pureStreamGen(Gen.boolean, _)),
              int.flatMap(pureStreamGen(Gen.anyInt, _)),
              int.flatMap(pureStreamGen(Gen.anyInt, _))
            ) { (b, s1, s2) =>
              for {
                interleavedStream <- s1.interleaveWith(s2)(b).runCollect
                b                 <- b.runCollect
                s1                <- s1.runCollect
                s2                <- s2.runCollect
                interleavedLists  = interleave(b, s1, s2)
              } yield assert(interleavedStream, equalTo(interleavedLists))
            }
          }
        ),
        testM("Stream.iterate")(
          assertM(Stream.iterate(1)(_ + 1).take(10).runCollect, equalTo((1 to 10).toList))
        ),
        testM("Stream.map")(checkM(pureStreamOfBytes, Gen.function(Gen.anyInt)) { (s, f) =>
          for {
            res1 <- s.map(f).runCollect
            res2 <- s.runCollect.map(_.map(f))
          } yield assert(res1, equalTo(res2))
        }),
        testM("Stream.mapAccum") {
          assertM(Stream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el)).runCollect, equalTo(List(1, 2, 3)))
        },
        suite("Stream.mapAccumM")(
          testM("mapAccumM happy path") {
            assertM(
              Stream(1, 1, 1)
                .mapAccumM[Any, Nothing, Int, Int](0)((acc, el) => IO.succeed((acc + el, acc + el)))
                .runCollect,
              equalTo(List(1, 2, 3))
            )
          },
          testM("mapAccumM error") {
            Stream(1, 1, 1)
              .mapAccumM(0)((_, _) => IO.fail("Ouch"))
              .runCollect
              .either
              .map(assert(_, isLeft(equalTo("Ouch"))))
          }
        ),
        testM("Stream.mapConcat")(checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
          for {
            res1 <- s.mapConcat(f).runCollect
            res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
          } yield assert(res1, equalTo(res2))
        }),
        testM("Stream.mapConcatChunk")(checkM(pureStreamOfBytes, Gen.function(smallChunks(Gen.anyInt))) { (s, f) =>
          for {
            res1 <- s.mapConcatChunk(f).runCollect
            res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
          } yield assert(res1, equalTo(res2))
        }),
        suite("Stream.mapConcatChunkM")(
          testM("mapConcatChunkM happy path") {
            checkM(pureStreamOfBytes, Gen.function(smallChunks(Gen.anyInt))) { (s, f) =>
              for {
                res1 <- s.mapConcatChunkM(b => UIO.succeed(f(b))).runCollect
                res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
              } yield assert(res1, equalTo(res2))
            }
          },
          testM("mapConcatChunkM error") {
            Stream(1, 2, 3)
              .mapConcatChunkM(_ => IO.fail("Ouch"))
              .runCollect
              .either
              .map(assert(_, equalTo(Left("Ouch"))))
          }
        ),
        suite("Stream.mapConcatM")(
          testM("mapConcatM happy path") {
            checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
              for {
                res1 <- s.mapConcatM(b => UIO.succeed(f(b))).runCollect
                res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
              } yield assert(res1, equalTo(res2))
            }
          },
          testM("mapConcatM error") {
            Stream(1, 2, 3)
              .mapConcatM(_ => IO.fail("Ouch"))
              .runCollect
              .either
              .map(assert(_, equalTo(Left("Ouch"))))
          }
        ),
        testM("Stream.mapError") {
          Stream
            .fail("123")
            .mapError(_.toInt)
            .runCollect
            .either
            .map(assert(_, isLeft(equalTo(123))))
        },
        testM("Stream.mapErrorCause") {
          Stream
            .halt(Cause.fail("123"))
            .mapErrorCause(_.map(_.toInt))
            .runCollect
            .either
            .map(assert(_, isLeft(equalTo(123))))
        },
        testM("Stream.mapM") {
          checkM(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(successes(Gen.anyByte))) { (data, f) =>
            val s = Stream.fromIterable(data)

            for {
              l <- s.mapM(f).runCollect
              r <- IO.foreach(data)(f)
            } yield assert(l, equalTo(r))
          }
        },
        testM("Stream.repeatEffect")(
          assertM(
            Stream
              .repeatEffect(IO.succeed(1))
              .take(2)
              .run(Sink.collectAll[Int]),
            equalTo(List(1, 1))
          )
        ),
        testM("Stream.repeatEffectWith")(
          (for {
            ref <- Ref.make[List[Int]](Nil)
            _ <- ZStream
                  .repeatEffectWith(ref.update(1 :: _), ZSchedule.spaced(10.millis))
                  .take(2)
                  .run(Sink.drain)
            result <- ref.get
          } yield assert(result, equalTo(List(1, 1)))).provide(Clock.Live)
        ),
        suite("Stream.mapMPar")(
          testM("foreachParN equivalence") {
            checkM(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(successes(Gen.anyByte))) { (data, f) =>
              val s = Stream.fromIterable(data)

              for {
                l <- s.mapMPar(8)(f).runCollect
                r <- IO.foreachParN(8)(data)(f)
              } yield assert(l, equalTo(r))
            }
          },
          testM("order when n = 1") {
            for {
              queue  <- Queue.unbounded[Int]
              _      <- Stream.range(0, 9).mapMPar(1)(queue.offer).runDrain
              result <- queue.takeAll
            } yield assert(result, equalTo(result.sorted))
          },
          testM("interruption propagation") {
            for {
              interrupted <- Ref.make(false)
              latch       <- Promise.make[Nothing, Unit]
              fib <- Stream(())
                      .mapMPar(1) { _ =>
                        (latch.succeed(()) *> ZIO.never).onInterrupt(interrupted.set(true))
                      }
                      .runDrain
                      .fork
              _      <- latch.await
              _      <- fib.interrupt
              result <- interrupted.get
            } yield assert(result, isTrue)
          },
          testM("guarantee ordering")(checkM(Gen.int(1, 4096), Gen.listOf(Gen.anyInt)) { (n: Int, m: List[Int]) =>
            for {
              mapM    <- Stream.fromIterable(m).mapM(UIO.succeed).runCollect
              mapMPar <- Stream.fromIterable(m).mapMPar(n)(UIO.succeed).runCollect
            } yield assert(n, isGreaterThan(0)) implies assert(mapM, equalTo(mapMPar))
          })
        ),
        suite("Stream merging")(
          testM("merge")(checkM(streamOfInts, streamOfInts) { (s1: Stream[String, Int], s2: Stream[String, Int]) =>
            for {
              mergedStream <- (s1 merge s2).runCollect.map(_.toSet).run
              mergedLists <- s1.runCollect
                              .zipWith(s2.runCollect) { (left, right) =>
                                left ++ right
                              }
                              .map(_.toSet)
                              .run
            } yield assert(!mergedStream.succeeded && !mergedLists.succeeded, isTrue) || assert(
              mergedStream,
              equalTo(mergedLists)
            )
          }),
          testM("mergeEither") {
            val s1 = Stream(1, 2)
            val s2 = Stream(1, 2)

            for {
              merge <- s1.mergeEither(s2).runCollect
            } yield assert[List[Either[Int, Int]]](merge, hasSameElements(List(Left(1), Left(2), Right(1), Right(2))))
          },
          testM("mergeWith") {
            val s1 = Stream(1, 2)
            val s2 = Stream(1, 2)

            for {
              merge <- s1.mergeWith(s2)(_.toString, _.toString).runCollect
            } yield assert(merge, hasSameElements(List("1", "2", "1", "2")))
          },
          testM("mergeWith short circuit") {
            val s1 = Stream(1, 2)
            val s2 = Stream(1, 2)

            assertM(
              s1.mergeWith(s2)(_.toString, _.toString)
                .run(Sink.succeed[String, String]("done")),
              equalTo("done")
            )
          },
          testM("mergeWith prioritizes failure") {
            val s1 = Stream.never
            val s2 = Stream.fail("Ouch")

            assertM(s1.mergeWith(s2)(_ => (), _ => ()).runCollect.either, isLeft(equalTo("Ouch")))
          }
        ),
        testM("Stream.orElse") {
          val s1 = Stream(1, 2, 3) ++ Stream.fail("Boom")
          val s2 = Stream(4, 5, 6)
          s1.orElse(s2).runCollect.map(assert(_, equalTo(List(1, 2, 3, 4, 5, 6))))
        },
        testM("Stream.paginate") {
          val s = (0, List(1, 2, 3))

          assertM(
            ZStream
              .paginate(s) {
                case (x, Nil)      => ZIO.succeed(x -> None)
                case (x, x0 :: xs) => ZIO.succeed(x -> Some(x0 -> xs))
              }
              .runCollect,
            equalTo(List(0, 1, 2, 3))
          )
        },
        suite("Stream.partitionEither")(
          testM("values") {
            Stream
              .range(0, 5)
              .partitionEither { i =>
                if (i % 2 == 0) ZIO.succeed(Left(i))
                else ZIO.succeed(Right(i))
              }
              .use {
                case (s1, s2) =>
                  for {
                    out1 <- s1.runCollect
                    out2 <- s2.runCollect
                  } yield assert(out1, equalTo(List(0, 2, 4))) && assert(out2, equalTo(List(1, 3, 5)))
              }
          },
          testM("errors") {
            (Stream.range(0, 1) ++ Stream.fail("Boom")).partitionEither { i =>
              if (i % 2 == 0) ZIO.succeed(Left(i))
              else ZIO.succeed(Right(i))
            }.use {
              case (s1, s2) =>
                for {
                  out1 <- s1.runCollect.either
                  out2 <- s2.runCollect.either
                } yield assert(out1, isLeft(equalTo("Boom"))) && assert(out2, isLeft(equalTo("Boom")))
            }
          },
          testM("backpressure") {
            Stream
              .range(0, 5)
              .partitionEither({ i =>
                if (i % 2 == 0) ZIO.succeed(Left(i))
                else ZIO.succeed(Right(i))
              }, 1)
              .use {
                case (s1, s2) =>
                  for {
                    ref       <- Ref.make[List[Int]](Nil)
                    latch1    <- Promise.make[Nothing, Unit]
                    fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
                    _         <- latch1.await
                    snapshot1 <- ref.get
                    other     <- s2.runCollect
                    _         <- fib.await
                    snapshot2 <- ref.get
                  } yield assert(snapshot1, equalTo(List(2, 0))) && assert(snapshot2, equalTo(List(4, 2, 0))) && assert(
                    other,
                    equalTo(
                      List(
                        1,
                        3,
                        5
                      )
                    )
                  )
              }
          }
        ),
        testM("Stream.peel") {
          val s = Stream('1', '2', ',', '3', '4')
          val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink
            .collectAllWhile[Char](_ == ',')
          assertM(s.peel(parser).use[Any, Int, (Int, Exit[Nothing, List[Char]])] {
            case (n, rest) => rest.runCollect.run.map(n -> _)
          }, equalTo((12, Success(List('3', '4')))))
        },
        testM("Stream.range") {
          assertM(Stream.range(0, 9).runCollect, equalTo((0 to 9).toList))
        },
        suite("Stream.repeat")(
          testM("repeat")(
            assertM(
              Stream(1)
                .repeat(Schedule.recurs(4))
                .run(Sink.collectAll[Int]),
              equalTo(List(1, 1, 1, 1, 1))
            )
          ),
          testM("short circuits")(
            (for {
              ref <- Ref.make[List[Int]](Nil)
              _ <- Stream
                    .fromEffect(ref.update(1 :: _))
                    .repeat(ZSchedule.spaced(10.millis))
                    .take(2)
                    .run(Sink.drain)
              result <- ref.get
            } yield assert(result, equalTo(List(1, 1)))).provide(Clock.Live)
          )
        ),
        suite("Stream.repeatEither")(
          testM("emits schedule output")(
            assertM(
              Stream(1)
                .repeatEither(Schedule.recurs(4))
                .run(Sink.collectAll[Either[Int, Int]]),
              equalTo(
                List(
                  Right(1),
                  Right(1),
                  Left(1),
                  Right(1),
                  Left(2),
                  Right(1),
                  Left(3),
                  Right(1),
                  Left(4)
                )
              )
            )
          ),
          testM("short circuits") {
            (for {
              ref <- Ref.make[List[Int]](Nil)
              _ <- Stream
                    .fromEffect(ref.update(1 :: _))
                    .repeatEither(ZSchedule.spaced(10.millis))
                    .take(3) // take one schedule output
                    .run(Sink.drain)
              result <- ref.get
            } yield assert(result, equalTo(List(1, 1)))).provide(Clock.Live)
          }
        ),
        suite("Stream.schedule")(
          testM("scheduleElementsWith")(
            assertM(
              Stream("A", "B", "C")
                .scheduleElementsWith(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))(identity, _.toString)
                .run(Sink.collectAll[String]),
              equalTo(List("A", "123", "B", "123", "C", "123"))
            )
          ),
          testM("scheduleElementsEither")(
            assertM(
              Stream("A", "B", "C")
                .scheduleElementsEither(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))
                .run(Sink.collectAll[Either[Int, String]]),
              equalTo(List(Right("A"), Left(123), Right("B"), Left(123), Right("C"), Left(123)))
            )
          ),
          testM("scheduleWith")(
            assertM(
              Stream("A", "B", "C", "A", "B", "C")
                .scheduleWith(Schedule.recurs(2) *> Schedule.fromFunction((_) => "Done"))(_.toLowerCase, identity)
                .run(Sink.collectAll[String]),
              equalTo(List("a", "b", "c", "Done", "a", "b", "c", "Done"))
            )
          ),
          testM("scheduleEither")(
            assertM(
              Stream("A", "B", "C")
                .scheduleEither(Schedule.recurs(2) *> Schedule.fromFunction((_) => "!"))
                .run(Sink.collectAll[Either[String, String]]),
              equalTo(List(Right("A"), Right("B"), Right("C"), Left("!")))
            )
          ),
          testM("repeated && assertspaced")(
            assertM(
              Stream("A", "B", "C")
                .scheduleElements(Schedule.once)
                .run(Sink.collectAll[String]),
              equalTo(List("A", "A", "B", "B", "C", "C"))
            )
          ),
          testM("short circuits in schedule")(
            assertM(
              Stream("A", "B", "C")
                .scheduleElements(Schedule.once)
                .take(4)
                .run(Sink.collectAll[String]),
              equalTo(List("A", "A", "B", "B"))
            )
          ),
          testM("short circuits after schedule")(
            assertM(
              Stream("A", "B", "C")
                .scheduleElements(Schedule.once)
                .take(3)
                .run(Sink.collectAll[String]),
              equalTo(List("A", "A", "B"))
            )
          )
        ),
        suite("Stream.take")(
          testM("take")(checkM(streamOfBytes, Gen.anyInt) { (s: Stream[String, Byte], n: Int) =>
            for {
              takeStreamResult <- s.take(n).runCollect.run
              takeListResult   <- s.runCollect.map(_.take(n)).run
            } yield assert(takeListResult.succeeded, isTrue) implies assert(
              takeStreamResult,
              equalTo(takeListResult)
            )
          }),
          testM("take short circuits")(
            for {
              ran    <- Ref.make(false)
              stream = (Stream(1) ++ Stream.fromEffect(ran.set(true)).drain).take(0)
              _      <- stream.run(Sink.drain)
              result <- ran.get
            } yield assert(result, isFalse)
          ),
          testM("take(0) short circuits")(
            for {
              units <- Stream.never.take(0).run(Sink.collectAll[Unit])
            } yield assert(units, equalTo(Nil))
          ),
          testM("take(1) short circuits")(
            for {
              ints <- (Stream(1) ++ Stream.never).take(1).run(Sink.collectAll[Int])
            } yield assert(ints, equalTo(List(1)))
          ),
          testM("takeUntil") {
            checkM(streamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
              for {
                streamTakeUntil <- s.takeUntil(p).runCollect.run
                listTakeUntil   <- s.runCollect.map(StreamUtils.takeUntil(_)(p)).run
              } yield assert(listTakeUntil.succeeded, isTrue) implies assert(
                streamTakeUntil,
                equalTo(listTakeUntil)
              )
            }
          },
          testM("takeWhile")(checkM(streamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
            for {
              streamTakeWhile <- s.takeWhile(p).runCollect.run
              listTakeWhile   <- s.runCollect.map(_.takeWhile(p)).run
            } yield assert(listTakeWhile.succeeded, isTrue) implies assert(streamTakeWhile, equalTo(listTakeWhile))
          }),
          testM("takeWhile short circuits")(
            assertM(
              (Stream(1) ++ Stream.fail("Ouch"))
                .takeWhile(_ => false)
                .runDrain
                .either,
              isRight(isUnit)
            )
          )
        ),
        testM("Stream.tap") {
          for {
            ref <- Ref.make(0)
            res <- Stream(1, 1).tap[Any, Nothing](a => ref.update(_ + a)).runCollect
            sum <- ref.get
          } yield assert(res, equalTo(List(1, 1))) && assert(sum, equalTo(2))
        },
        suite("Stream.timeout")(
          testM("succeed") {
            assertM(
              Stream
                .succeed(1)
                .timeout(Duration.Infinity)
                .runCollect,
              equalTo(List(1))
            )
          },
          testM("should interrupt stream") {
            assertM(
              Stream
                .range(0, 5)
                .tap(_ => ZIO.sleep(Duration.Infinity))
                .timeout(Duration.Zero)
                .runDrain
                .ignore
                .map(_ => true),
              isTrue
            )
          }
        ),
        suite("Stream.throttleEnforce")(
          testM("free elements") {
            assertM(
              Stream(1, 2, 3, 4)
                .throttleEnforce(0, Duration.Infinity)(_ => 0)
                .runCollect,
              equalTo(List(1, 2, 3, 4))
            )
          },
          testM("no bandwidth") {
            assertM(
              Stream(1, 2, 3, 4)
                .throttleEnforce(0, Duration.Infinity)(_ => 1)
                .runCollect,
              equalTo(Nil)
            )
          }
        ),
        suite("Stream.throttleShape")(testM("free elements") {
          assertM(
            Stream(1, 2, 3, 4)
              .throttleShape(1, Duration.Infinity)(_ => 0)
              .runCollect,
            equalTo(List(1, 2, 3, 4))
          )
        }),
        testM("Stream.toQueue")(checkM(smallChunks(Gen.anyInt)) { c: Chunk[Int] =>
          val s = Stream.fromChunk(c)
          assertM(
            s.toQueue(1000).use { queue: Queue[Take[Nothing, Int]] =>
              waitForSize(queue, c.length + 1) *> queue.takeAll
            },
            equalTo(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End)
          )
        }),
        testM("Stream.toQueueUnbounded")(checkM(smallChunks(Gen.anyInt)) { c: Chunk[Int] =>
          val s = Stream.fromChunk(c)
          assertM(
            s.toQueueUnbounded.use { queue: Queue[Take[Nothing, Int]] =>
              waitForSize(queue, c.length + 1) *> queue.takeAll
            },
            equalTo(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End)
          )
        }),
        suite("Stream.transduce")(
          testM("transduce") {
            val s = Stream('1', '2', ',', '3', '4')
            val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink
              .collectAllWhile[Char](_ == ',')

            assertM(s.transduce(parser).runCollect, equalTo(List(12, 34)))
          },
          testM("no remainder") {
            val sink = Sink.fold(100)(_ % 2 == 0)((s, a: Int) => (s + a, Chunk[Int]()))
            assertM(ZStream(1, 2, 3, 4).transduce(sink).runCollect, equalTo(List(101, 105, 104)))
          },
          testM("with remainder") {
            val sink = Sink
              .fold[Int, Int, (Int, Boolean)]((0, true))(_._2) { (s, a: Int) =>
                a match {
                  case 1 => ((s._1 + 100, true), Chunk.empty)
                  case 2 => ((s._1 + 100, true), Chunk.empty)
                  case 3 => ((s._1 + 3, false), Chunk.single(a + 1))
                  case _ => ((s._1 + 4, false), Chunk.empty)
                }
              }
              .map(_._1)

            assertM(ZStream(1, 2, 3).transduce(sink).runCollect, equalTo(List(203, 4)))
          },
          testM("with a sink that always signals more") {
            val sink = Sink.foldLeft(0)((s, a: Int) => s + a)
            assertM(ZStream(1, 2, 3).transduce(sink).runCollect, equalTo(List(1 + 2 + 3)))
          },
          testM("managed") {
            final class TestSink(ref: Ref[Int]) extends ZSink[Any, Throwable, Int, Int, List[Int]] {
              type State = (List[Int], Boolean)

              def extract(state: State) = UIO.succeed((state._1, Chunk.empty))

              def initial = UIO.succeed((Nil, true))

              def step(state: State, a: Int) =
                for {
                  i <- ref.get
                  _ <- if (i != 1000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
                } yield (List(a, a), false)

              def cont(state: State) = state._2
            }

            val stream = ZStream(1, 2, 3, 4)

            for {
              resource <- Ref.make(0)
              sink     = ZManaged.make(resource.set(1000).as(new TestSink(resource)))(_ => resource.set(2000))
              result   <- stream.transduceManaged(sink).runCollect
              i        <- resource.get
              _        <- if (i != 2000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
            } yield assert(result, equalTo(List(List(1, 1), List(2, 2), List(3, 3), List(4, 4))))
          },
          testM("propagate managed error") {
            val fail = "I'm such a failure!"
            val sink = ZManaged.fail(fail)
            assertM(ZStream(1, 2, 3).transduceManaged(sink).runCollect.either, isLeft(equalTo(fail)))
          }
        ),
        testM("Stream.unfold") {
          assertM(
            Stream
              .unfold(0) { i =>
                if (i < 10) Some((i, i + 1))
                else None
              }
              .runCollect,
            equalTo((0 to 9).toList)
          )
        },
        testM("Stream.unfoldM") {
          assertM(
            Stream
              .unfoldM(0) { i =>
                if (i < 10) IO.succeed(Some((i, i + 1)))
                else IO.succeed(None)
              }
              .runCollect,
            equalTo((0 to 9).toList)
          )
        },
        testM("Stream.unNone")(checkM(Gen.small(pureStreamGen(Gen.option(Gen.anyInt), _))) { s =>
          for {
            res1 <- (s.unNone.runCollect)
            res2 <- (s.runCollect.map(_.flatten))
          } yield assert(res1, equalTo(res2))
        }),
        suite("Stream.unTake")(
          testM("unTake happy path") {
            assertM(
              Stream
                .range(0, 10)
                .toQueue[Nothing, Int](1)
                .use { q =>
                  Stream.fromQueue(q).unTake.run(Sink.collectAll[Int])
                },
              equalTo((0 to 10).toList)
            )
          },
          testM("unTake with error") {
            val e = new RuntimeException("boom")
            assertM(
              (Stream.range(0, 10) ++ Stream.fail(e))
                .toQueue[Throwable, Int](1)
                .use { q =>
                  Stream.fromQueue(q).unTake.run(Sink.collectAll[Int])
                }
                .run,
              fails(equalTo(e))
            )
          }
        ),
        suite("Stream zipping")(
          testM("zipWith") {
            val s1 = Stream(1, 2, 3)
            val s2 = Stream(1, 2)

            assertM(s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _))).runCollect, equalTo(List(2, 4)))
          },
          testM("zipWithIndex")(checkM(pureStreamOfBytes) { s =>
            for {
              res1 <- (s.zipWithIndex.runCollect)
              res2 <- (s.runCollect.map(_.zipWithIndex))
            } yield assert(res1, equalTo(res2))
          }),
          testM("zipWith ignore RHS") {
            val s1 = Stream(1, 2, 3)
            val s2 = Stream(1, 2)
            assertM(s1.zipWith(s2)((a, _) => a).runCollect, equalTo(List(1, 2, 3)))
          },
          testM("zipWith prioritizes failure") {
            assertM(
              Stream.never
                .zipWith(Stream.fail("Ouch"))((_, _) => None)
                .runCollect
                .either,
              isLeft(equalTo("Ouch"))
            )
          },
          testM("zipWithLatest") {
            import zio.test.environment.TestClock

            for {
              clock <- TestClock.make(TestClock.DefaultData)
              s1    = Stream.iterate(0)(_ + 1).fixed(100.millis).provide(clock)
              s2    = Stream.iterate(0)(_ + 1).fixed(70.millis).provide(clock)
              s3    = s1.zipWithLatest(s2)((_, _))
              q     <- Queue.unbounded[(Int, Int)]
              _     <- s3.foreach(q.offer).fork
              a     <- q.take
              _     <- clock.clock.setTime(70.millis)
              b     <- q.take
              _     <- clock.clock.setTime(100.millis)
              c     <- q.take
              _     <- clock.clock.setTime(140.millis)
              d     <- q.take
              _     <- clock.clock.setTime(210.millis)
            } yield assert(List(a, b, c, d), equalTo(List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2)))
          }
        ),
        testM("Stream.toInputStream") {
          val stream = Stream(1, 2, 3).map(_.toByte)
          for {
            streamResult <- stream.runCollect
            inputStreamResult <- stream.toInputStream.use { inputStream =>
                                  ZIO.succeed(
                                    Iterator
                                      .continually(inputStream.read)
                                      .takeWhile(_ != -1)
                                      .map(_.toByte)
                                      .toList
                                  )
                                }
          } yield assert(streamResult, equalTo(inputStreamResult))
        }
      )
    )
