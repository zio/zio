package zio.stream

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext

import ZStreamGen._

import zio._
import zio.clock.Clock
import zio.duration._
import zio.stm.TQueue
import zio.test.Assertion._
import zio.test.TestAspect.flaky
import zio.test._
import zio.test.environment.TestClock

object ZStreamSpec extends ZIOBaseSpec {
  import ZIOTag._
  import ZStream.Take

  def inParallel(action: => Unit)(implicit ec: ExecutionContext): Unit =
    ec.execute(() => action)

  def spec =
    suite("ZStreamSpec")(
      suite("Combinators")(
        suite("absolve")(
          testM("happy path")(checkM(tinyListOf(Gen.anyInt)) { xs =>
            val stream = ZStream.fromIterable(xs.map(Right(_)))
            assertM(stream.absolve.runCollect)(equalTo(xs))
          }),
          testM("failure")(checkM(tinyListOf(Gen.anyInt)) { xs =>
            val stream = ZStream.fromIterable(xs.map(Right(_))) ++ ZStream.succeed(Left("Ouch"))
            assertM(stream.absolve.runCollect.run)(fails(equalTo("Ouch")))
          }),
          testM("round-trip #1")(checkM(tinyListOf(Gen.anyInt), Gen.anyString) { (xs, s) =>
            val xss    = ZStream.fromIterable(xs.map(Right(_)))
            val stream = xss ++ ZStream(Left(s)) ++ xss
            for {
              res1 <- stream.runCollect
              res2 <- stream.absolve.either.runCollect
            } yield assert(res1)(startsWith(res2))
          }),
          testM("round-trip #2")(checkM(tinyListOf(Gen.anyInt), Gen.anyString) { (xs, s) =>
            val xss    = ZStream.fromIterable(xs)
            val stream = xss ++ ZStream.fail(s)
            for {
              res1 <- stream.runCollect.run
              res2 <- stream.either.absolve.runCollect.run
            } yield assert(res1)(fails(equalTo(s))) && assert(res2)(fails(equalTo(s)))
          })
        ) @@ TestAspect.jvmOnly, // This is horrendously slow on Scala.js for some reason
        testM("access") {
          for {
            result <- ZStream.access[String](identity).provide("test").runHead.get

          } yield assert(result)(equalTo("test"))
        },
        suite("accessM")(
          testM("accessM") {
            for {
              result <- ZStream.accessM[String](ZIO.succeed(_)).provide("test").runHead.get
            } yield assert(result)(equalTo("test"))
          },
          testM("accessM fails") {
            for {
              result <- ZStream.accessM[Int](_ => ZIO.fail("fail")).provide(0).runHead.run
            } yield assert(result)(fails(equalTo("fail")))
          } @@ zioTag(errors)
        ),
        suite("accessStream")(
          testM("accessStream") {
            for {
              result <- ZStream.accessStream[String](ZStream.succeed(_)).provide("test").runHead.get
            } yield assert(result)(equalTo("test"))
          },
          testM("accessStream fails") {
            for {
              result <- ZStream.accessStream[Int](_ => ZStream.fail("fail")).provide(0).runHead.run
            } yield assert(result)(fails(equalTo("fail")))
          } @@ zioTag(errors)
        ),
        suite("aggregateAsync")(
          testM("aggregateAsync999") {
            ZStream(1, 1, 1, 1)
              .aggregateAsync(ZTransducer.foldUntil(List[Int](), 3)((acc, el) => el :: acc))
              .runCollect
              .map { result =>
                assert(result.flatten)(equalTo(List(1, 1, 1, 1))) &&
                assert(result.forall(_.length <= 3))(isTrue)
              }
          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")
            assertM(
              ZStream(1, 1, 1, 1)
                .aggregateAsync(ZTransducer.die(e))
                .runCollect
                .run
            )(dies(equalTo(e)))
          },
          testM("error propagation") {
            val e = new RuntimeException("Boom")

            assertM(
              ZStream(1, 1)
                .aggregateAsync(ZTransducer.foldLeftM(Nil)((_, _) => ZIO.die(e)))
                .runCollect
                .run
            )(dies(equalTo(e)))
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = ZTransducer.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
                if (el == 1) UIO.succeedNow(el :: acc)
                else
                  (latch.succeed(()) *> ZIO.infinity)
                    .onInterrupt(cancelled.set(true))
              }
              fiber  <- ZStream(1, 1, 2).aggregateAsync(sink).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result)(isTrue)
          },
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = ZTransducer.fromEffect {
                (latch.succeed(()) *> ZIO.infinity)
                  .onInterrupt(cancelled.set(true))
              }
              fiber  <- ZStream(1, 1, 2).aggregateAsync(sink).runCollect.untraced.fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result)(isTrue)
          },
          testM("leftover handling") {
            val data = List(1, 2, 2, 3, 2, 3)
            assertM(
              ZStream(data: _*)
                .aggregateAsync(
                  ZTransducer.foldWeighted(List[Int]())((_, x: Int) => x.toLong, 4)((acc, el) => el :: acc)
                )
                .map(_.reverse)
                .runCollect
                .map(_.flatten)
            )(equalTo(data))
          }
        ),
        suite("aggregate")(
          testM("aggregate") {
            assertM(
              ZStream('1', '2', ',', '3', '4')
                .aggregate(ZTransducer.collectAllWhile(_.isDigit))
                .map(_.mkString.toInt)
                .runCollect
            )(equalTo(List(12, 34)))
          },
          testM("no remainder") {
            assertM(
              ZStream(1, 2, 3, 4)
                .aggregate(ZTransducer.fold(100)(_ % 2 == 0)(_ + _))
                .runCollect
            )(equalTo(List(101, 105, 104)))
          },
          testM("with a sink that always signals more") {
            assertM(
              ZStream(1, 2, 3)
                .aggregate(ZTransducer.fold(0)(_ => true)(_ + _))
                .runCollect
            )(equalTo(List(1 + 2 + 3)))
          },
          // testM("managed") {
          //   final class TestSink(ref: Ref[Int]) extends ZSink[Any, Throwable, Int, Int, List[Int]] {
          //     type State = (List[Int], Boolean)

          //     def extract(state: State) = UIO.succeedNow((state._1, Chunk.empty))

          //     def initial = UIO.succeedNow((Nil, true))

          //     def step(state: State, a: Int) =
          //       for {
          //         i <- ref.get
          //         _ <- if (i != 1000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
          //       } yield (List(a, a), false)

          //     def cont(state: State) = state._2
          //   }

          //   val stream = ZStream(1, 2, 3, 4)

          //   for {
          //     resource <- Ref.make(0)
          //     sink     = ZManaged.make(resource.set(1000).as(new TestSink(resource)))(_ => resource.set(2000))
          //     result   <- stream.aggregateManaged(sink).runCollect
          //     i        <- resource.get
          //     _        <- if (i != 2000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
          //   } yield assert(result)(equalTo(List(List(1, 1), List(2, 2), List(3, 3), List(4, 4))))
          // },
          testM("propagate managed error") {
            val fail = "I'm such a failure!"
            val t    = ZTransducer.fail(fail)
            assertM(ZStream(1, 2, 3).aggregate(t).runCollect.either)(isLeft(equalTo(fail)))
          }
        ),
        suite("aggregateAsyncWithinEither")(
          testM("aggregateAsyncWithinEither") {
            assertM(
              ZStream(1, 1, 1, 1, 2, 2)
                .aggregateAsyncWithinEither(
                  ZTransducer
                    .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                      if (el == 1) (el :: acc._1, true)
                      else if (el == 2 && acc._1.isEmpty) (el :: acc._1, false)
                      else (el :: acc._1, false)
                    }
                    .map(_._1),
                  Schedule.spaced(30.minutes)
                )
                .runCollect
            )(
              equalTo(List(Right(List(2, 1, 1, 1, 1)), Right(List(2))))
            )
          },
          testM("error propagation 1") {
            val e = new RuntimeException("Boom")
            assertM(
              ZStream(1, 1, 1, 1)
                .aggregateAsyncWithinEither(ZTransducer.die(e), Schedule.spaced(30.minutes))
                .runCollect
                .run
            )(dies(equalTo(e)))
          } @@ zioTag(errors),
          testM("error propagation 2") {
            val e = new RuntimeException("Boom")

            assertM(
              ZStream(1, 1)
                .aggregateAsyncWithinEither(
                  ZTransducer.foldM[Any, Nothing, Int, List[Int]](List[Int]())(_ => true)((_, _) => ZIO.die(e)),
                  Schedule.spaced(30.minutes)
                )
                .runCollect
                .run
            )(dies(equalTo(e)))
          } @@ zioTag(errors),
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = ZTransducer.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
                if (el == 1) UIO.succeed(el :: acc)
                else
                  (latch.succeed(()) *> ZIO.infinity)
                    .onInterrupt(cancelled.set(true))
              }
              fiber <- ZStream(1, 1, 2)
                        .aggregateAsyncWithinEither(sink, Schedule.spaced(30.minutes))
                        .runCollect
                        .untraced
                        .fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result)(isTrue)
          } @@ zioTag(interruption),
          testM("interruption propagation") {
            for {
              latch     <- Promise.make[Nothing, Unit]
              cancelled <- Ref.make(false)
              sink = ZTransducer.fromEffect {
                (latch.succeed(()) *> ZIO.infinity)
                  .onInterrupt(cancelled.set(true))
              }
              fiber <- ZStream(1, 1, 2)
                        .aggregateAsyncWithinEither(sink, Schedule.spaced(30.minutes))
                        .runCollect
                        .untraced
                        .fork
              _      <- latch.await
              _      <- fiber.interrupt
              result <- cancelled.get
            } yield assert(result)(isTrue)
          } @@ zioTag(interruption),
          testM("aggregateAsyncWithinEitherLeftoverHandling") {
            val data = List(1, 2, 2, 3, 2, 3)
            assertM(
              for {
                f <- (ZStream(data: _*)
                      .aggregateAsyncWithinEither(
                        ZTransducer
                          .foldWeighted(List[Int]())((_, i: Int) => i.toLong, 4)((acc, el) => el :: acc)
                          .map(_.reverse),
                        Schedule.spaced(100.millis)
                      )
                      .collect {
                        case Right(v) => v
                      }
                      .runCollect
                      .map(_.flatten))
                      .fork
                _      <- TestClock.adjust(31.minutes)
                result <- f.join
              } yield result
            )(equalTo(data))
          }
        ),
        suite("aggregateAsyncWithin")(
          testM("aggregateAsyncWithin") {
            assertM(
              ZStream(1, 1, 1, 1, 2, 2)
                .aggregateAsyncWithin(
                  ZTransducer
                    .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                      if (el == 1) (el :: acc._1, true)
                      else if (el == 2 && acc._1.isEmpty) (el :: acc._1, false)
                      else (el :: acc._1, false)
                    }
                    .map(_._1),
                  Schedule.spaced(30.minutes)
                )
                .runCollect
            )(equalTo(List(List(2, 1, 1, 1, 1), List(2))))
          }
        ),
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
        suite("broadcast")(
          testM("Values") {
            ZStream
              .range(0, 5)
              .broadcast(2, 12)
              .use {
                case s1 :: s2 :: Nil =>
                  for {
                    out1     <- s1.runCollect
                    out2     <- s2.runCollect
                    expected = Range(0, 5).toList
                  } yield assert(out1)(equalTo(expected)) && assert(out2)(equalTo(expected))
                case _ =>
                  UIO(assert(())(Assertion.nothing))
              }
          },
          testM("Errors") {
            (ZStream.range(0, 1) ++ ZStream.fail("Boom")).broadcast(2, 12).use {
              case s1 :: s2 :: Nil =>
                for {
                  out1     <- s1.runCollect.either
                  out2     <- s2.runCollect.either
                  expected = Left("Boom")
                } yield assert(out1)(equalTo(expected)) && assert(out2)(equalTo(expected))
              case _ =>
                UIO(assert(())(Assertion.nothing))
            }
          },
          testM("BackPressure") {
            ZStream
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
                  } yield assert(snapshot1)(equalTo(List(2, 1, 0))) && assert(snapshot2)(
                    equalTo(Range(0, 5).toList.reverse)
                  )
                case _ =>
                  UIO(assert(())(Assertion.nothing))
              }
          },
          testM("Unsubscribe") {
            ZStream.range(0, 5).broadcast(2, 2).use {
              case s1 :: s2 :: Nil =>
                for {
                  _    <- s1.process.use_(ZIO.unit).ignore
                  out2 <- s2.runCollect
                } yield assert(out2)(equalTo(Range(0, 5).toList))
              case _ =>
                UIO(assert(())(Assertion.nothing))
            }
          }
        ),
        suite("buffer")(
          testM("maintains elements and ordering")(checkM(tinyListOf(Gen.chunkOf(Gen.anyInt))) { list =>
            assertM(
              ZStream
                .fromChunks(list: _*)
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
        suite("bufferDropping")(
          testM("buffer the Stream with Error") {
            val e = new RuntimeException("boom")
            assertM(
              (ZStream.range(1, 1000) ++ ZStream.fail(e) ++ ZStream.range(1001, 2000))
                .bufferDropping(2)
                .runCollect
                .run
            )(fails(equalTo(e)))
          },
          testM("fast producer progress independently") {
            for {
              ref    <- Ref.make(List.empty[Int])
              latch1 <- Promise.make[Nothing, Unit]
              latch2 <- Promise.make[Nothing, Unit]
              latch3 <- Promise.make[Nothing, Unit]
              latch4 <- Promise.make[Nothing, Unit]
              s1 = ZStream(0) ++ ZStream
                .fromEffect(latch1.await)
                .flatMap(_ => ZStream.range(1, 17).ensuring(latch2.succeed(())))
              s2 = ZStream
                .fromEffect(latch3.await)
                .flatMap(_ => ZStream.range(17, 25).ensuring(latch4.succeed(())))
              s = (s1 ++ s2).bufferDropping(8)
              snapshots <- s.process.use { as =>
                            for {
                              zero      <- as
                              _         <- latch1.succeed(())
                              _         <- latch2.await
                              _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                              snapshot1 <- ref.get
                              _         <- latch3.succeed(())
                              _         <- latch4.await
                              _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                              snapshot2 <- ref.get
                            } yield (zero, snapshot1, snapshot2)
                          }
            } yield assert(snapshots._1)(equalTo(Chunk.single(0))) && assert(snapshots._2)(
              equalTo(List(8, 7, 6, 5, 4, 3, 2, 1))
            ) &&
              assert(snapshots._3)(equalTo(List(24, 23, 22, 21, 20, 19, 18, 17, 8, 7, 6, 5, 4, 3, 2, 1)))
          }
        ),
        suite("bufferSliding")(
          testM("buffer the Stream with Error") {
            val e = new RuntimeException("boom")
            assertM(
              (ZStream.range(1, 1000) ++ ZStream.fail(e) ++ ZStream.range(1001, 2000))
                .bufferSliding(2)
                .runCollect
                .run
            )(fails(equalTo(e)))
          },
          testM("fast producer progress independently") {
            for {
              ref    <- Ref.make(List.empty[Int])
              latch1 <- Promise.make[Nothing, Unit]
              latch2 <- Promise.make[Nothing, Unit]
              latch3 <- Promise.make[Nothing, Unit]
              latch4 <- Promise.make[Nothing, Unit]
              s1 = ZStream(0) ++ ZStream
                .fromEffect(latch1.await)
                .flatMap(_ => ZStream.range(1, 17).ensuring(latch2.succeed(())))
              s2 = ZStream
                .fromEffect(latch3.await)
                .flatMap(_ => ZStream.range(17, 25).ensuring(latch4.succeed(())))
              s = (s1 ++ s2).bufferSliding(8)
              snapshots <- s.process.use { as =>
                            for {
                              zero      <- as
                              _         <- latch1.succeed(())
                              _         <- latch2.await
                              _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                              snapshot1 <- ref.get
                              _         <- latch3.succeed(())
                              _         <- latch4.await
                              _         <- as.flatMap(a => ref.update(a.toList ::: _)).repeat(Schedule.recurs(7))
                              snapshot2 <- ref.get
                            } yield (zero, snapshot1, snapshot2)
                          }
            } yield assert(snapshots._1)(equalTo(Chunk.single(0))) && assert(snapshots._2)(
              equalTo(List(16, 15, 14, 13, 12, 11, 10, 9))
            ) &&
              assert(snapshots._3)(equalTo(List(24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9)))
          }
        ),
        suite("bufferUnbounded")(
          testM("buffer the Stream")(checkM(Gen.listOf(Gen.anyInt)) { list =>
            assertM(
              ZStream
                .fromIterable(list)
                .bufferUnbounded
                .runCollect
            )(equalTo(list))
          }),
          testM("buffer the Stream with Error") {
            val e = new RuntimeException("boom")
            assertM((ZStream.range(0, 10) ++ ZStream.fail(e)).bufferUnbounded.runCollect.run)(fails(equalTo(e)))
          },
          testM("fast producer progress independently") {
            for {
              ref   <- Ref.make(List[Int]())
              latch <- Promise.make[Nothing, Unit]
              s = ZStream
                .fromEffect(UIO.succeedNow(()))
                .flatMap(_ => ZStream.range(1, 1000).tap(i => ref.update(i :: _)).ensuring(latch.succeed(())))
                .bufferUnbounded
              l <- s.process.use { as =>
                    for {
                      _ <- as
                      _ <- latch.await
                      l <- ref.get
                    } yield l
                  }
            } yield assert(l.reverse)(equalTo(Range(1, 1000).toList))
          }
        ),
        suite("catchAllCause")(
          testM("recovery from errors") {
            val s1 = ZStream(1, 2) ++ ZStream.fail("Boom")
            val s2 = ZStream(3, 4)
            s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
          },
          testM("recovery from defects") {
            val s1 = ZStream(1, 2) ++ ZStream.dieMessage("Boom")
            val s2 = ZStream(3, 4)
            s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
          },
          testM("happy path") {
            val s1 = ZStream(1, 2)
            val s2 = ZStream(3, 4)
            s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2))))
          },
          testM("executes finalizers") {
            for {
              fins   <- Ref.make(List[String]())
              s1     = (ZStream(1, 2) ++ ZStream.fail("Boom")).ensuring(fins.update("s1" :: _))
              s2     = (ZStream(3, 4) ++ ZStream.fail("Boom")).ensuring(fins.update("s2" :: _))
              _      <- s1.catchAllCause(_ => s2).runCollect.run
              result <- fins.get
            } yield assert(result)(equalTo(List("s2", "s1")))
          }
        ),
        suite("catchSome")(
          testM("recovery from some errors") {
            val s1 = ZStream(1, 2) ++ ZStream.fail("Boom")
            val s2 = ZStream(3, 4)
            s1.catchSome { case "Boom" => s2 }.runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
          },
          testM("fails stream when partial function does not match") {
            val s1 = ZStream(1, 2) ++ ZStream.fail("Boom")
            val s2 = ZStream(3, 4)
            s1.catchSome { case "Boomer" => s2 }.runCollect.either.map(assert(_)(isLeft(equalTo("Boom"))))
          }
        ),
        suite("catchSomeCause")(
          testM("recovery from some errors") {
            val s1 = ZStream(1, 2) ++ ZStream.halt(Cause.Fail("Boom"))
            val s2 = ZStream(3, 4)
            s1.catchSomeCause { case Cause.Fail("Boom") => s2 }.runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
          },
          testM("halts stream when partial function does not match") {
            val s1 = ZStream(1, 2) ++ ZStream.fail("Boom")
            val s2 = ZStream(3, 4)
            s1.catchSomeCause { case Cause.empty => s2 }.runCollect.either.map(assert(_)(isLeft(equalTo("Boom"))))
          }
        ),
        testM("collect") {
          assertM(ZStream(Left(1), Right(2), Left(3)).collect {
            case Right(n) => n
          }.runCollect)(equalTo(List(2)))
        },
        suite("collectM")(
          testM("collectM") {
            assertM(
              ZStream(Left(1), Right(2), Left(3)).collectM {
                case Right(n) => ZIO(n * 2)
              }.runCollect
            )(equalTo(List(4)))
          },
          testM("collectM fails") {
            assertM(
              ZStream(Left(1), Right(2), Left(3)).collectM {
                case Right(_) => ZIO.fail("Ouch")
              }.runDrain.either
            )(isLeft(isNonEmptyString))
          }
        ),
        testM("collectSome")(checkM(Gen.bounded(0, 5)(pureStreamGen(Gen.option(Gen.anyInt), _))) { s =>
          for {
            res1 <- (s.collectSome.runCollect)
            res2 <- (s.runCollect.map(_.flatten))
          } yield assert(res1)(equalTo(res2))
        }),
        suite("collectWhile")(
          testM("collectWhile") {
            assertM(ZStream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile {
              case Some(v) => v
            }.runCollect)(equalTo(List(1, 2, 3)))
          },
          testM("collectWhile short circuits") {
            assertM((ZStream(Option(1)) ++ ZStream.fail("Ouch")).collectWhile {
              case None => 1
            }.runDrain.either)(isRight(isUnit))
          }
        ),
        suite("collectWhileM")(
          testM("collectWhileM") {
            assertM(
              ZStream(Some(1), Some(2), Some(3), None, Some(4)).collectWhileM {
                case Some(v) => ZIO(v * 2)
              }.runCollect
            )(equalTo(List(2, 4, 6)))
          },
          testM("collectWhileM short circuits") {
            assertM(
              (ZStream(Option(1)) ++ ZStream.fail("Ouch"))
                .collectWhileM[Any, String, Int] {
                  case None => ZIO.succeedNow(1)
                }
                .runDrain
                .either
            )(isRight(isUnit))
          },
          testM("collectWhileM fails") {
            assertM(
              ZStream(Some(1), Some(2), Some(3), None, Some(4)).collectWhileM {
                case Some(_) => ZIO.fail("Ouch")
              }.runDrain.either
            )(isLeft(isNonEmptyString))
          }
        ),
        suite("collectWhileSuccess")(
          testM("happy path") {
            assertM(
              ZStream
                .range(0, 10)
                .toQueue(1)
                .use(q => ZStream.fromQueue(q).collectWhileSuccess.runCollect)
                .map(_.flatMap(_.toList))
            )(equalTo(Range(0, 10).toList))
          },
          testM("errors") {
            val e = new RuntimeException("boom")
            assertM(
              (ZStream.range(0, 10) ++ ZStream.fail(e))
                .toQueue(1)
                .use(q => ZStream.fromQueue(q).collectWhileSuccess.runCollect)
                .run
            )(fails(equalTo(e)))
          } @@ zioTag(errors)
        ),
        suite("concat")(
          testM("concat")(checkM(streamOfBytes, streamOfBytes) { (s1, s2) =>
            for {
              listConcat   <- s1.runCollect.zipWith(s2.runCollect)(_ ++ _).run
              streamConcat <- (s1 ++ s2).runCollect.run
            } yield assert(streamConcat.succeeded && listConcat.succeeded)(isTrue) implies assert(streamConcat)(
              equalTo(listConcat)
            )
          }),
          testM("finalizer order") {
            for {
              log <- Ref.make[List[String]](Nil)
              _ <- (ZStream.finalizer(log.update("Second" :: _)) ++ ZStream
                    .finalizer(log.update("First" :: _))).runDrain
              execution <- log.get
            } yield assert(execution)(equalTo(List("First", "Second")))
          }
        ),
        suite("distributedWithDynamic")(
          testM("ensures no race between subscription and stream end") {
            ZStream.empty.distributedWithDynamic(1, _ => UIO.succeedNow(_ => true)).use { add =>
              val subscribe = ZStream.unwrap(add.map {
                case (_, queue) =>
                  ZStream.fromQueue(queue).collectWhileSuccess
              })
              Promise.make[Nothing, Unit].flatMap { onEnd =>
                subscribe.ensuring(onEnd.succeed(())).runDrain.fork *>
                  onEnd.await *>
                  subscribe.runDrain *>
                  ZIO.succeedNow(assertCompletes)
              }
            }
          }
        ),
        suite("drain")(
          testM("drain")(
            for {
              ref <- Ref.make(List[Int]())
              _   <- ZStream.range(0, 10).mapM(i => ref.update(i :: _)).drain.runDrain
              l   <- ref.get
            } yield assert(l.reverse)(equalTo(Range(0, 10).toList))
          ),
          testM("isn't too eager") {
            (ZStream(1) ++ ZStream.fail("fail")).drain.process.use(pull => assertM(pull.run)(succeeds(isEmpty)))
          }
        ),
        suite("drainFork")(
          testM("runs the other stream in the background") {
            for {
              latch <- Promise.make[Nothing, Unit]
              _ <- ZStream
                    .fromEffect(latch.await)
                    .drainFork(ZStream.fromEffect(latch.succeed(())))
                    .runDrain
            } yield assertCompletes
          },
          testM("interrupts the background stream when the foreground exits") {
            for {
              bgInterrupted <- Ref.make(false)
              latch         <- Promise.make[Nothing, Unit]
              _ <- (ZStream(1, 2, 3) ++ ZStream.fromEffect(latch.await).drain)
                    .drainFork(
                      ZStream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(bgInterrupted.set(true)))
                    )
                    .runDrain
              result <- bgInterrupted.get
            } yield assert(result)(isTrue)
          } @@ zioTag(interruption),
          testM("fails the foreground stream if the background fails with a typed error") {
            assertM(ZStream.never.drainFork(ZStream.fail("Boom")).runDrain.run)(fails(equalTo("Boom")))
          } @@ zioTag(errors),
          testM("fails the foreground stream if the background fails with a defect") {
            val ex = new RuntimeException("Boom")
            assertM(ZStream.never.drainFork(ZStream.die(ex)).runDrain.run)(dies(equalTo(ex)))
          } @@ zioTag(errors)
        ),
        testM("drop")(checkM(streamOfBytes, Gen.anyInt) { (s: ZStream[Any, String, Byte], n: Int) =>
          for {
            dropStreamResult <- s.drop(n.toLong).runCollect.run
            dropListResult   <- s.runCollect.map(_.drop(n)).run
          } yield assert(dropListResult.succeeded)(isTrue) implies assert(dropStreamResult)(equalTo(dropListResult))
        }),
        testM("dropUntil") {
          checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
            for {
              res1 <- s.dropUntil(p).runCollect
              res2 <- s.runCollect.map(_.dropWhile(!p(_)).drop(1))
            } yield assert(res1)(equalTo(res2))
          }
        },
        suite("dropWhile")(
          testM("dropWhile")(
            checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) {
              (s: ZStream[Any, String, Byte], p: Byte => Boolean) =>
                for {
                  res1 <- s.dropWhile(p).runCollect
                  res2 <- s.runCollect.map(_.dropWhile(p))
                } yield assert(res1)(equalTo(res2))
            }
          ),
          testM("short circuits") {
            assertM(
              (ZStream(1) ++ ZStream.fail("Ouch"))
                .take(1)
                .dropWhile(_ => true)
                .runDrain
                .either
            )(isRight(isUnit))
          }
        ),
        testM("either") {
          val s = ZStream(1, 2, 3) ++ ZStream.fail("Boom")
          s.either.runCollect.map(assert(_)(equalTo(List(Right(1), Right(2), Right(3), Left("Boom")))))
        },
        testM("ensuring") {
          for {
            log <- Ref.make[List[String]](Nil)
            _ <- (for {
                  _ <- ZStream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                  _ <- ZStream.fromEffect(log.update("Use" :: _))
                } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
            execution <- log.get
          } yield assert(execution)(equalTo(List("Ensuring", "Release", "Use", "Acquire")))
        },
        testM("ensuringFirst") {
          for {
            log <- Ref.make[List[String]](Nil)
            _ <- (for {
                  _ <- ZStream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                  _ <- ZStream.fromEffect(log.update("Use" :: _))
                } yield ()).ensuringFirst(log.update("Ensuring" :: _)).runDrain
            execution <- log.get
          } yield assert(execution)(equalTo(List("Release", "Ensuring", "Use", "Acquire")))
        },
        testM("filter")(checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
          for {
            res1 <- s.filter(p).runCollect
            res2 <- s.runCollect.map(_.filter(p))
          } yield assert(res1)(equalTo(res2))
        }),
        testM("filterM")(checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
          for {
            res1 <- s.filterM(s => IO.succeed(p(s))).runCollect
            res2 <- s.runCollect.map(_.filter(p))
          } yield assert(res1)(equalTo(res2))
        }),
        suite("flatMap")(
          testM("deep flatMap stack safety") {
            def fib(n: Int): ZStream[Any, Nothing, Int] =
              if (n <= 1) ZStream.succeed(n)
              else
                fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => ZStream.succeed(a + b)))

            val stream   = fib(20)
            val expected = 6765

            assertM(stream.runCollect)(equalTo(List(expected)))
          } @@ TestAspect.jvmOnly, // Too slow on Scala.js
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
          } @@ TestAspect.jvmOnly, // Too slow on Scala.js
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
        suite("flatMapPar")(
          testM("guarantee ordering")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { (m: List[Int]) =>
            for {
              flatMap    <- ZStream.fromIterable(m).flatMap(i => ZStream(i, i)).runCollect
              flatMapPar <- ZStream.fromIterable(m).flatMapPar(1)(i => ZStream(i, i)).runCollect
            } yield assert(flatMap)(equalTo(flatMapPar))
          }),
          testM("consistent with flatMap")(checkM(Gen.int(1, Int.MaxValue), Gen.small(Gen.listOfN(_)(Gen.anyInt))) {
            (n, m) =>
              for {
                flatMap    <- ZStream.fromIterable(m).flatMap(i => ZStream(i, i)).runCollect.map(_.toSet)
                flatMapPar <- ZStream.fromIterable(m).flatMapPar(n)(i => ZStream(i, i)).runCollect.map(_.toSet)
              } yield assert(n)(isGreaterThan(0)) implies assert(flatMap)(equalTo(flatMapPar))
          }),
          testM("short circuiting") {
            assertM(
              ZStream
                .mergeAll(2)(
                  ZStream.never,
                  ZStream(1)
                )
                .take(1)
                .runCollect
            )(equalTo(List(1)))
          },
          testM("interruption propagation") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              fiber <- ZStream(())
                        .flatMapPar(1)(_ =>
                          ZStream.fromEffect(
                            (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                          )
                        )
                        .runDrain
                        .fork
              _         <- latch.await
              _         <- fiber.interrupt
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue)
          },
          testM("inner errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- ZStream(
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         ),
                         ZStream.fromEffect(latch.await *> ZIO.fail("Ouch"))
                       ).flatMapPar(2)(identity).runDrain.either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
          },
          testM("outer errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> ZIO.fail("Ouch")))
                         .flatMapPar(2) { _ =>
                           ZStream.fromEffect(
                             (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .runDrain
                         .either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
          },
          testM("inner defects interrupt all fibers") {
            val ex = new RuntimeException("Ouch")

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- ZStream(
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         ),
                         ZStream.fromEffect(latch.await *> ZIO.die(ex))
                       ).flatMapPar(2)(identity).runDrain.run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
          },
          testM("outer defects interrupt all fibers") {
            val ex = new RuntimeException()

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> ZIO.die(ex)))
                         .flatMapPar(2) { _ =>
                           ZStream.fromEffect(
                             (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .runDrain
                         .run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
          },
          testM("finalizer ordering") {
            for {
              execution <- Ref.make[List[String]](Nil)
              inner = ZStream
                .bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
              _ <- ZStream
                    .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ =>
                      execution.update("OuterRelease" :: _)
                    )
                    .flatMapPar(2)(identity)
                    .runDrain
              results <- execution.get
            } yield assert(results)(equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
          }
        ),
        suite("flatMapParSwitch")(
          testM("guarantee ordering no parallelism") {
            for {
              lastExecuted <- Ref.make(false)
              semaphore    <- Semaphore.make(1)
              _ <- ZStream(1, 2, 3, 4)
                    .flatMapParSwitch(1) { i =>
                      if (i > 3) ZStream.bracket(UIO.unit)(_ => lastExecuted.set(true)).flatMap(_ => ZStream.empty)
                      else ZStream.managed(semaphore.withPermitManaged).flatMap(_ => ZStream.never)
                    }
                    .runDrain
              result <- semaphore.withPermit(lastExecuted.get)
            } yield assert(result)(isTrue)
          },
          testM("guarantee ordering with parallelism") {
            for {
              lastExecuted <- Ref.make(0)
              semaphore    <- Semaphore.make(4)
              _ <- ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                    .flatMapParSwitch(4) { i =>
                      if (i > 8)
                        ZStream.bracket(UIO.unit)(_ => lastExecuted.update(_ + 1)).flatMap(_ => ZStream.empty)
                      else ZStream.managed(semaphore.withPermitManaged).flatMap(_ => ZStream.never)
                    }
                    .runDrain
              result <- semaphore.withPermits(4)(lastExecuted.get)
            } yield assert(result)(equalTo(4))
          },
          testM("short circuiting") {
            assertM(
              ZStream(ZStream.never, ZStream(1))
                .flatMapParSwitch(2)(identity)
                .take(1)
                .runCollect
            )(equalTo(List(1)))
          },
          testM("interruption propagation") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              fiber <- ZStream(())
                        .flatMapParSwitch(1)(_ =>
                          ZStream.fromEffect(
                            (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                          )
                        )
                        .runCollect
                        .fork
              _         <- latch.await
              _         <- fiber.interrupt
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue)
          } @@ flaky,
          testM("inner errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- ZStream(
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         ),
                         ZStream.fromEffect(latch.await *> IO.fail("Ouch"))
                       ).flatMapParSwitch(2)(identity).runDrain.either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
          } @@ flaky,
          testM("outer errors interrupt all fibers") {
            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> IO.fail("Ouch")))
                         .flatMapParSwitch(2) { _ =>
                           ZStream.fromEffect(
                             (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .runDrain
                         .either
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
          },
          testM("inner defects interrupt all fibers") {
            val ex = new RuntimeException("Ouch")

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- ZStream(
                         ZStream.fromEffect(
                           (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                         ),
                         ZStream.fromEffect(latch.await *> ZIO.die(ex))
                       ).flatMapParSwitch(2)(identity).runDrain.run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
          },
          testM("outer defects interrupt all fibers") {
            val ex = new RuntimeException()

            for {
              substreamCancelled <- Ref.make[Boolean](false)
              latch              <- Promise.make[Nothing, Unit]
              result <- (ZStream(()) ++ ZStream.fromEffect(latch.await *> ZIO.die(ex)))
                         .flatMapParSwitch(2) { _ =>
                           ZStream.fromEffect(
                             (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                           )
                         }
                         .runDrain
                         .run
              cancelled <- substreamCancelled.get
            } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
          },
          testM("finalizer ordering") {
            for {
              execution <- Ref.make(List.empty[String])
              inner     = ZStream.bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
              _ <- ZStream
                    .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ =>
                      execution.update("OuterRelease" :: _)
                    )
                    .flatMapParSwitch(2)(identity)
                    .runDrain
              results <- execution.get
            } yield assert(results)(equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
          }
        ),
        suite("foreach")(
          testM("foreach") {
            for {
              ref <- Ref.make(0)
              _   <- ZStream(1, 1, 1, 1, 1).foreach[Any, Nothing](a => ref.update(_ + a))
              sum <- ref.get
            } yield assert(sum)(equalTo(5))
          },
          testM("foreachWhile") {
            for {
              ref <- Ref.make(0)
              _ <- ZStream(1, 1, 1, 1, 1, 1).foreachWhile[Any, Nothing](a =>
                    ref.modify(sum =>
                      if (sum >= 3) (false, sum)
                      else (true, sum + a)
                    )
                  )
              sum <- ref.get
            } yield assert(sum)(equalTo(3))
          },
          testM("foreachWhile short circuits") {
            for {
              flag <- Ref.make(true)
              _ <- (ZStream(true, true, false) ++ ZStream.fromEffect(flag.set(false)).drain)
                    .foreachWhile(ZIO.succeedNow)
              skipped <- flag.get
            } yield assert(skipped)(isTrue)
          }
        ),
        testM("forever") {
          for {
            ref <- Ref.make(0)
            _ <- ZStream(1).forever.foreachWhile[Any, Nothing](_ =>
                  ref.modify(sum => (if (sum >= 9) false else true, sum + 1))
                )
            sum <- ref.get
          } yield assert(sum)(equalTo(10))
        },
        suite("groupBy")(
          testM("values") {
            val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
            assertM(
              ZStream
                .fromIterable(words)
                .groupByKey(identity, 8192) {
                  case (k, s) =>
                    ZStream.fromEffect(s.runCollect.map(l => k -> l.size))
                }
                .runCollect
                .map(_.toMap)
            )(equalTo((0 to 100).map((_.toString -> 1000)).toMap))
          },
          testM("first") {
            val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
            assertM(
              ZStream
                .fromIterable(words)
                .groupByKey(identity, 1050)
                .first(2) {
                  case (k, s) =>
                    ZStream.fromEffect(s.runCollect.map(l => k -> l.size))
                }
                .runCollect
                .map(_.toMap)
            )(equalTo((0 to 1).map((_.toString -> 1000)).toMap))
          },
          testM("filter") {
            val words = List.fill(1000)(0 to 100).flatten
            assertM(
              ZStream
                .fromIterable(words)
                .groupByKey(identity, 1050)
                .filter(_ <= 5) {
                  case (k, s) =>
                    ZStream.fromEffect(s.runCollect.map(l => k -> l.size))
                }
                .runCollect
                .map(_.toMap)
            )(equalTo((0 to 5).map((_ -> 1000)).toMap))
          },
          testM("outer errors") {
            val words = List("abc", "test", "test", "foo")
            assertM(
              (ZStream.fromIterable(words) ++ ZStream.fail("Boom"))
                .groupByKey(identity) { case (_, s) => s.drain }
                .runCollect
                .either
            )(isLeft(equalTo("Boom")))
          }
        ) @@ TestAspect.jvmOnly,
        suite("haltWhen")(
          suite("haltWhen(Promise)")(
            testM("halts after the current element") {
              for {
                interrupted <- Ref.make(false)
                latch       <- Promise.make[Nothing, Unit]
                halt        <- Promise.make[Nothing, Unit]
                _ <- ZStream
                      .fromEffect(latch.await.onInterrupt(interrupted.set(true)))
                      .haltWhen(halt)
                      .runDrain
                      .fork
                _      <- halt.succeed(())
                _      <- latch.succeed(())
                result <- interrupted.get
              } yield assert(result)(isFalse)
            },
            testM("propagates errors") {
              for {
                halt <- Promise.make[String, Nothing]
                _    <- halt.fail("Fail")
                result <- ZStream(1)
                           .haltWhen(halt)
                           .runDrain
                           .either
              } yield assert(result)(isLeft(equalTo("Fail")))
            } @@ zioTag(errors)
          ),
          suite("haltWhen(IO)")(
            testM("halts after the current element") {
              for {
                interrupted <- Ref.make(false)
                latch       <- Promise.make[Nothing, Unit]
                halt        <- Promise.make[Nothing, Unit]
                _ <- ZStream
                      .fromEffect(latch.await.onInterrupt(interrupted.set(true)))
                      .haltWhen(halt.await)
                      .runDrain
                      .fork
                _      <- halt.succeed(())
                _      <- latch.succeed(())
                result <- interrupted.get
              } yield assert(result)(isFalse)
            },
            testM("propagates errors") {
              for {
                halt <- Promise.make[String, Nothing]
                _    <- halt.fail("Fail")
                result <- ZStream(0).forever
                           .haltWhen(halt.await)
                           .runDrain
                           .either
              } yield assert(result)(isLeft(equalTo("Fail")))
            } @@ zioTag(errors)
          )
        ),
        suite("haltAfter")(
          testM("halts after given duration") {
            assertWithChunkCoordination(List(Chunk(1), Chunk(2), Chunk(3), Chunk(4))) {
              c =>
                assertM(
                  for {
                    fiber <- ZStream
                              .fromQueue(c.queue)
                              .collectWhileSuccess
                              .haltAfter(5.seconds)
                              .tap(_ => c.proceed)
                              .runCollect
                              .fork
                    _      <- c.offer *> TestClock.adjust(3.seconds) *> c.awaitNext
                    _      <- c.offer *> TestClock.adjust(3.seconds) *> c.awaitNext
                    _      <- c.offer *> TestClock.adjust(3.seconds) *> c.awaitNext
                    _      <- c.offer
                    result <- fiber.join
                  } yield result
                )(equalTo(List(Chunk(1), Chunk(2), Chunk(3))))
            }
          },
          testM("will process first chunk") {
            for {
              queue  <- Queue.unbounded[Int]
              fiber  <- ZStream.fromQueue(queue).haltAfter(5.seconds).runCollect.fork
              _      <- TestClock.adjust(6.seconds)
              _      <- queue.offer(1)
              result <- fiber.join
            } yield assert(result)(equalTo(List(1)))
          }
        ),
        testM("grouped")(
          assertM(ZStream(1, 2, 3, 4).grouped(2).runCollect)(equalTo(List(List(1, 2), List(3, 4))))
        ),
        suite("groupedWithin")(
          testM("group based on time passed") {
            assertWithChunkCoordination(List(Chunk(1, 2), Chunk(3, 4), Chunk.single(5))) { c =>
              val stream = ZStream
                .fromQueue(c.queue)
                .collectWhileSuccess
                .flattenChunks
                .groupedWithin(10, 2.seconds)
                .tap(_ => c.proceed)

              assertM(for {
                f      <- stream.runCollect.fork
                _      <- c.offer *> TestClock.adjust(2.seconds) *> c.awaitNext
                _      <- c.offer *> TestClock.adjust(2.seconds) *> c.awaitNext
                _      <- c.offer
                result <- f.join
              } yield result)(equalTo(List(List(1, 2), List(3, 4), List(5))))
            }
          },
          testM("group immediately when chunk size is reached") {
            assertM(ZStream(1, 2, 3, 4).groupedWithin(2, 10.seconds).runCollect)(equalTo(List(List(1, 2), List(3, 4))))
          }
        ),
        testM("interleave") {
          val s1 = ZStream(2, 3)
          val s2 = ZStream(5, 6, 7)

          assertM(s1.interleave(s2).runCollect)(equalTo(List(2, 5, 3, 6, 7)))
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
              }
              else
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
            } yield assert(interleavedStream)(equalTo(interleavedLists))
          }
        },
        suite("Stream.intersperse")(
          testM("intersperse several") {
            Stream(1, 2, 3, 4)
              .map(_.toString)
              .intersperse("@")
              .runCollect
              .map(result => assert(result)(equalTo(List("1", "@", "2", "@", "3", "@", "4"))))
          },
          testM("intersperse several with begin and end") {
            Stream(1, 2, 3, 4)
              .map(_.toString)
              .intersperse("[", "@", "]")
              .runCollect
              .map(result => assert(result)(equalTo(List("[", "1", "@", "2", "@", "3", "@", "4", "]"))))
          },
          testM("intersperse single") {
            Stream(1)
              .map(_.toString)
              .intersperse("@")
              .runCollect
              .map(result => assert(result)(equalTo(List("1"))))
          },
          testM("intersperse single with begin and end") {
            Stream(1)
              .map(_.toString)
              .intersperse("[", "@", "]")
              .runCollect
              .map(result => assert(result)(equalTo(List("[", "1", "]"))))
          }
        ),
        suite("interruptWhen")(
          suite("interruptWhen(Promise)")(
            testM("interrupts the current element") {
              for {
                interrupted <- Ref.make(false)
                latch       <- Promise.make[Nothing, Unit]
                halt        <- Promise.make[Nothing, Unit]
                started     <- Promise.make[Nothing, Unit]
                fiber <- ZStream
                          .fromEffect((started.succeed(()) *> latch.await).onInterrupt(interrupted.set(true)))
                          .interruptWhen(halt)
                          .runDrain
                          .fork
                _      <- started.await *> halt.succeed(())
                _      <- fiber.await
                result <- interrupted.get
              } yield assert(result)(isTrue)
            },
            testM("propagates errors") {
              for {
                halt <- Promise.make[String, Nothing]
                _    <- halt.fail("Fail")
                result <- ZStream(1)
                           .haltWhen(halt)
                           .runDrain
                           .either
              } yield assert(result)(isLeft(equalTo("Fail")))
            } @@ zioTag(errors)
          ) @@ zioTag(interruption),
          suite("interruptWhen(IO)")(
            testM("interrupts the current element") {
              for {
                interrupted <- Ref.make(false)
                latch       <- Promise.make[Nothing, Unit]
                halt        <- Promise.make[Nothing, Unit]
                started     <- Promise.make[Nothing, Unit]
                fiber <- ZStream
                          .fromEffect((started.succeed(()) *> latch.await).onInterrupt(interrupted.set(true)))
                          .interruptWhen(halt.await)
                          .runDrain
                          .fork
                _      <- started.await *> halt.succeed(())
                _      <- fiber.await
                result <- interrupted.get
              } yield assert(result)(isTrue)
            },
            testM("propagates errors") {
              for {
                halt <- Promise.make[String, Nothing]
                _    <- halt.fail("Fail")
                result <- ZStream(1).forever
                           .haltWhen(halt.await)
                           .runDrain
                           .either
              } yield assert(result)(isLeft(equalTo("Fail")))
            } @@ zioTag(errors)
          ) @@ zioTag(interruption)
        ),
        suite("interruptAfter")(
          testM("interrupts after given duration") {
            assertWithChunkCoordination(List(Chunk(1), Chunk(2), Chunk(3))) { c =>
              assertM(
                for {
                  fiber <- ZStream
                            .fromQueue(c.queue)
                            .collectWhileSuccess
                            .interruptAfter(5.seconds)
                            .tap(_ => c.proceed)
                            .runCollect
                            .fork
                  _      <- c.offer *> TestClock.adjust(3.seconds) *> c.awaitNext
                  _      <- c.offer *> TestClock.adjust(3.seconds) *> c.awaitNext
                  _      <- c.offer
                  result <- fiber.join
                } yield result
              )(equalTo(List(Chunk(1), Chunk(2))))
            }
          },
          testM("interrupts before first chunk") {
            for {
              queue  <- Queue.unbounded[Int]
              fiber  <- ZStream.fromQueue(queue).interruptAfter(5.seconds).runCollect.fork
              _      <- TestClock.adjust(6.seconds)
              _      <- queue.offer(1)
              result <- fiber.join
            } yield assert(result)(isEmpty)
          }
        ) @@ zioTag(interruption),
        suite("managed")(
          testM("preserves interruptibility of effect") {
            for {
              interruptible <- ZStream.managed(ZManaged.fromEffect(ZIO.checkInterruptible(UIO.succeed(_)))).runHead
              uninterruptible <- ZStream
                                  .managed(ZManaged.fromEffectUninterruptible(ZIO.checkInterruptible(UIO.succeed(_))))
                                  .runHead
            } yield assert(interruptible)(isSome(equalTo(InterruptStatus.Interruptible))) &&
              assert(uninterruptible)(isSome(equalTo(InterruptStatus.Uninterruptible)))
          }
        ),
        testM("map")(checkM(pureStreamOfBytes, Gen.function(Gen.anyInt)) { (s, f) =>
          for {
            res1 <- s.map(f).runCollect
            res2 <- s.runCollect.map(_.map(f))
          } yield assert(res1)(equalTo(res2))
        }),
        testM("mapAccum") {
          assertM(ZStream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el)).runCollect)(equalTo(List(1, 2, 3)))
        },
        suite("mapAccumM")(
          testM("mapAccumM happy path") {
            assertM(
              ZStream(1, 1, 1)
                .mapAccumM[Any, Nothing, Int, Int](0)((acc, el) => IO.succeed((acc + el, acc + el)))
                .runCollect
            )(equalTo(List(1, 2, 3)))
          },
          testM("mapAccumM error") {
            ZStream(1, 1, 1)
              .mapAccumM(0)((_, _) => IO.fail("Ouch"))
              .runCollect
              .either
              .map(assert(_)(isLeft(equalTo("Ouch"))))
          } @@ zioTag(errors)
        ),
        testM("mapConcat")(checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
          for {
            res1 <- s.mapConcat(f).runCollect
            res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
          } yield assert(res1)(equalTo(res2))
        }),
        testM("mapConcatChunk")(checkM(pureStreamOfBytes, Gen.function(Gen.chunkOf(Gen.anyInt))) { (s, f) =>
          for {
            res1 <- s.mapConcatChunk(f).runCollect
            res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
          } yield assert(res1)(equalTo(res2))
        }),
        suite("mapConcatChunkM")(
          testM("mapConcatChunkM happy path") {
            checkM(pureStreamOfBytes, Gen.function(Gen.chunkOf(Gen.anyInt))) { (s, f) =>
              for {
                res1 <- s.mapConcatChunkM(b => UIO.succeedNow(f(b))).runCollect
                res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
              } yield assert(res1)(equalTo(res2))
            }
          },
          testM("mapConcatChunkM error") {
            ZStream(1, 2, 3)
              .mapConcatChunkM(_ => IO.fail("Ouch"))
              .runCollect
              .either
              .map(assert(_)(equalTo(Left("Ouch"))))
          }
        ),
        suite("mapConcatM")(
          testM("mapConcatM happy path") {
            checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
              for {
                res1 <- s.mapConcatM(b => UIO.succeedNow(f(b))).runCollect
                res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
              } yield assert(res1)(equalTo(res2))
            }
          },
          testM("mapConcatM error") {
            ZStream(1, 2, 3)
              .mapConcatM(_ => IO.fail("Ouch"))
              .runCollect
              .either
              .map(assert(_)(equalTo(Left("Ouch"))))
          }
        ),
        testM("mapError") {
          ZStream
            .fail("123")
            .mapError(_.toInt)
            .runCollect
            .either
            .map(assert(_)(isLeft(equalTo(123))))
        },
        testM("mapErrorCause") {
          ZStream
            .halt(Cause.fail("123"))
            .mapErrorCause(_.map(_.toInt))
            .runCollect
            .either
            .map(assert(_)(isLeft(equalTo(123))))
        },
        testM("mapM") {
          checkM(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(Gen.successes(Gen.anyByte))) { (data, f) =>
            val s = ZStream.fromIterable(data)

            for {
              l <- s.mapM(f).runCollect
              r <- IO.foreach(data)(f)
            } yield assert(l)(equalTo(r))
          }
        },
        suite("mapMPar")(
          testM("foreachParN equivalence") {
            checkNM(10)(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(Gen.successes(Gen.anyByte))) { (data, f) =>
              val s = ZStream.fromIterable(data)

              for {
                l <- s.mapMPar(8)(f).runCollect
                r <- IO.foreachParN(8)(data)(f)
              } yield assert(l)(equalTo(r))
            }
          },
          testM("order when n = 1") {
            for {
              queue  <- Queue.unbounded[Int]
              _      <- ZStream.range(0, 9).mapMPar(1)(queue.offer).runDrain
              result <- queue.takeAll
            } yield assert(result)(equalTo(result.sorted))
          },
          testM("interruption propagation") {
            for {
              interrupted <- Ref.make(false)
              latch       <- Promise.make[Nothing, Unit]
              fib <- ZStream(())
                      .mapMPar(1)(_ => (latch.succeed(()) *> ZIO.infinity).onInterrupt(interrupted.set(true)))
                      .runDrain
                      .fork
              _      <- latch.await
              _      <- fib.interrupt
              result <- interrupted.get
            } yield assert(result)(isTrue)
          },
          testM("guarantee ordering")(checkM(Gen.int(1, 4096), Gen.listOf(Gen.anyInt)) { (n: Int, m: List[Int]) =>
            for {
              mapM    <- ZStream.fromIterable(m).mapM(UIO.succeedNow).runCollect
              mapMPar <- ZStream.fromIterable(m).mapMPar(n)(UIO.succeedNow).runCollect
            } yield assert(n)(isGreaterThan(0)) implies assert(mapM)(equalTo(mapMPar))
          })
        ),
        suite("mergeTerminateLeft")(
          testM("terminates as soon as the first stream terminates") {
            for {
              queue1  <- Queue.unbounded[Int]
              queue2  <- Queue.unbounded[Int]
              stream1 = ZStream.fromQueue(queue1)
              stream2 = ZStream.fromQueue(queue2)
              fiber   <- stream1.mergeTerminateLeft(stream2).runCollect.fork
              _       <- queue1.offer(1) *> TestClock.adjust(1.second)
              _       <- queue1.offer(2) *> TestClock.adjust(1.second)
              _       <- queue1.shutdown *> TestClock.adjust(1.second)
              _       <- queue2.offer(3)
              result  <- fiber.join
            } yield assert(result)(equalTo(List(1, 2)))
          }
        ),
        suite("mergeTerminateRight")(
          testM("terminates as soon as the second stream terminates") {
            for {
              queue1  <- Queue.unbounded[Int]
              queue2  <- Queue.unbounded[Int]
              stream1 = ZStream.fromQueue(queue1)
              stream2 = ZStream.fromQueue(queue2)
              fiber   <- stream1.mergeTerminateRight(stream2).runCollect.fork
              _       <- queue2.offer(2) *> TestClock.adjust(1.second)
              _       <- queue2.offer(3) *> TestClock.adjust(1.second)
              _       <- queue2.shutdown *> TestClock.adjust(1.second)
              _       <- queue1.offer(1)
              result  <- fiber.join
            } yield assert(result)(equalTo(List(2, 3)))
          }
        ),
        suite("mergeTerminateEither")(
          testM("terminates as soon as either stream terminates") {
            for {
              queue1  <- Queue.unbounded[Int]
              queue2  <- Queue.unbounded[Int]
              stream1 = ZStream.fromQueue(queue1)
              stream2 = ZStream.fromQueue(queue2)
              fiber   <- stream1.mergeTerminateEither(stream2).runCollect.fork
              _       <- queue1.shutdown
              _       <- TestClock.adjust(1.second)
              _       <- queue2.offer(1)
              result  <- fiber.join
            } yield assert(result)(isEmpty)
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
        suite("partitionEither")(
          testM("allows repeated runs without hanging") {
            val stream = ZStream
              .fromIterable[Int](Seq.empty)
              .partitionEither(i => ZIO.succeedNow(if (i % 2 == 0) Left(i) else Right(i)))
              .map { case (evens, odds) => evens.mergeEither(odds) }
              .use(_.runCollect)
            assertM(ZIO.collectAll(Range(0, 100).toList.map(_ => stream)).map(_ => 0))(equalTo(0))
          },
          testM("values") {
            ZStream
              .range(0, 6)
              .partitionEither { i =>
                if (i % 2 == 0) ZIO.succeedNow(Left(i))
                else ZIO.succeedNow(Right(i))
              }
              .use {
                case (s1, s2) =>
                  for {
                    out1 <- s1.runCollect
                    out2 <- s2.runCollect
                  } yield assert(out1)(equalTo(List(0, 2, 4))) && assert(out2)(equalTo(List(1, 3, 5)))
              }
          },
          testM("errors") {
            (ZStream.range(0, 1) ++ ZStream.fail("Boom")).partitionEither { i =>
              if (i % 2 == 0) ZIO.succeedNow(Left(i))
              else ZIO.succeedNow(Right(i))
            }.use {
              case (s1, s2) =>
                for {
                  out1 <- s1.runCollect.either
                  out2 <- s2.runCollect.either
                } yield assert(out1)(isLeft(equalTo("Boom"))) && assert(out2)(isLeft(equalTo("Boom")))
            }
          },
          testM("backpressure") {
            ZStream
              .range(0, 6)
              .partitionEither(
                i =>
                  if (i % 2 == 0) ZIO.succeedNow(Left(i))
                  else ZIO.succeedNow(Right(i)),
                1
              )
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
                  } yield assert(snapshot1)(equalTo(List(2, 0))) && assert(snapshot2)(equalTo(List(4, 2, 0))) && assert(
                    other
                  )(
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
        testM("peel") {
          val sink: ZSink[Any, Nothing, Int, Chunk[Int]] = ZSink {
            ZManaged.succeed {
              case Some(inputs) => ZIO.fail(Right(inputs))
              case None         => ZIO.fail(Right(Chunk.empty))
            }
          }

          ZStream.fromChunks(Chunk(1, 2, 3), Chunk(4, 5, 6)).peel(sink).use {
            case (chunk, rest) =>
              rest.runCollect.map { rest =>
                assert(chunk)(equalTo(Chunk(1, 2, 3))) &&
                assert(rest)(equalTo(List(4, 5, 6)))
              }
          }
        },
        testM("orElse") {
          val s1 = ZStream(1, 2, 3) ++ ZStream.fail("Boom")
          val s2 = ZStream(4, 5, 6)
          s1.orElse(s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4, 5, 6))))
        },
        testM("orElseEither") {
          val s1 = ZStream.succeed(1) ++ ZStream.fail("Boom")
          val s2 = ZStream.succeed(2)
          s1.orElseEither(s2).runCollect.map(assert(_)(equalTo(List(Left(1), Right(2)))))
        },
        testM("orElseFail") {
          val s1 = ZStream.succeed(1) ++ ZStream.fail("Boom")
          s1.orElseFail("Boomer").runCollect.either.map(assert(_)(isLeft(equalTo("Boomer"))))
        },
        testM("orElseOptional") {
          val s1 = ZStream.succeed(1) ++ ZStream.fail(None)
          val s2 = ZStream.succeed(2)
          s1.orElseOptional(s2).runCollect.map(assert(_)(equalTo(List(1, 2))))
        },
        testM("orElseSucceed") {
          val s1 = ZStream.succeed(1) ++ ZStream.fail("Boom")
          s1.orElseSucceed(2).runCollect.map(assert(_)(equalTo(List(1, 2))))
        },
        suite("repeat")(
          testM("repeat")(
            assertM(
              ZStream(1)
                .repeat(Schedule.recurs(4))
                .runCollect
            )(equalTo(List(1, 1, 1, 1, 1)))
          ),
          testM("short circuits")(
            for {
              ref <- Ref.make[List[Int]](Nil)
              fiber <- ZStream
                        .fromEffect(ref.update(1 :: _))
                        .repeat(Schedule.spaced(10.millis))
                        .take(2)
                        .runDrain
                        .fork
              _      <- TestClock.adjust(50.millis)
              _      <- fiber.join
              result <- ref.get
            } yield assert(result)(equalTo(List(1, 1)))
          )
        ),
        suite("repeatEither")(
          testM("emits schedule output")(
            assertM(
              ZStream(1)
                .repeatEither(Schedule.recurs(4))
                .runCollect
            )(
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
            for {
              ref <- Ref.make[List[Int]](Nil)
              fiber <- ZStream
                        .fromEffect(ref.update(1 :: _))
                        .repeatEither(Schedule.spaced(10.millis))
                        .take(3) // take one schedule output
                        .runDrain
                        .fork
              _      <- TestClock.adjust(50.millis)
              _      <- fiber.join
              result <- ref.get
            } yield assert(result)(equalTo(List(1, 1)))
          }
        ),
        testM("right") {
          val s1 = ZStream.succeed(Right(1)) ++ ZStream.succeed(Left(0))
          s1.right.runCollect.either.map(assert(_)(isLeft(equalTo(None))))
        },
        testM("rightOrFail") {
          val s1 = ZStream.succeed(Right(1)) ++ ZStream.succeed(Left(0))
          s1.rightOrFail(-1).runCollect.either.map(assert(_)(isLeft(equalTo(-1))))
        },
        suite("runHead")(
          testM("nonempty stream")(
            assertM(ZStream(1, 2, 3, 4).runHead)(equalTo(Some(1)))
          ),
          testM("empty stream")(
            assertM(ZStream.empty.runHead)(equalTo(None))
          )
        ),
        suite("runLast")(
          testM("nonempty stream")(
            assertM(ZStream(1, 2, 3, 4).runLast)(equalTo(Some(4)))
          ),
          testM("empty stream")(
            assertM(ZStream.empty.runLast)(equalTo(None))
          )
        ),
        suite("schedule")(
          testM("scheduleWith")(
            assertM(
              ZStream("A", "B", "C", "A", "B", "C")
                .scheduleWith(Schedule.recurs(2) *> Schedule.fromFunction((_) => "Done"))(_.toLowerCase, identity)
                .runCollect
            )(equalTo(List("a", "b", "c", "Done", "a", "b", "c", "Done")))
          ),
          testM("scheduleEither")(
            assertM(
              ZStream("A", "B", "C")
                .scheduleEither(Schedule.recurs(2) *> Schedule.fromFunction((_) => "!"))
                .runCollect
            )(equalTo(List(Right("A"), Right("B"), Right("C"), Left("!"))))
          )
        ),
        suite("scheduleElements")(
          testM("scheduleElementsWith")(
            assertM(
              ZStream("A", "B", "C")
                .scheduleElementsWith(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))(identity, _.toString)
                .runCollect
            )(equalTo(List("A", "123", "B", "123", "C", "123")))
          ),
          testM("scheduleElementsEither")(
            assertM(
              ZStream("A", "B", "C")
                .scheduleElementsEither(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))
                .runCollect
            )(equalTo(List(Right("A"), Left(123), Right("B"), Left(123), Right("C"), Left(123))))
          ),
          testM("repeated && assertspaced")(
            assertM(
              ZStream("A", "B", "C")
                .scheduleElements(Schedule.once)
                .runCollect
            )(equalTo(List("A", "A", "B", "B", "C", "C")))
          ),
          testM("short circuits in schedule")(
            assertM(
              ZStream("A", "B", "C")
                .scheduleElements(Schedule.once)
                .take(4)
                .runCollect
            )(equalTo(List("A", "A", "B", "B")))
          ),
          testM("short circuits after schedule")(
            assertM(
              ZStream("A", "B", "C")
                .scheduleElements(Schedule.once)
                .take(3)
                .runCollect
            )(equalTo(List("A", "A", "B")))
          )
        ),
        testM("some") {
          val s1 = ZStream.succeed(Some(1)) ++ ZStream.succeed(None)
          s1.some.runCollect.either.map(assert(_)(isLeft(equalTo(None))))
        },
        testM("someOrElse") {
          val s1 = ZStream.succeed(Some(1)) ++ ZStream.succeed(None)
          s1.someOrElse(-1).runCollect.map(assert(_)(equalTo(List(1, -1))))
        },
        testM("someOrFail") {
          val s1 = ZStream.succeed(Some(1)) ++ ZStream.succeed(None)
          s1.someOrFail(-1).runCollect.either.map(assert(_)(isLeft(equalTo(-1))))
        },
        suite("take")(
          testM("take")(checkM(streamOfBytes, Gen.anyInt) { (s: ZStream[Any, String, Byte], n: Int) =>
            for {
              takeStreamResult <- s.take(n.toLong).runCollect.run
              takeListResult   <- s.runCollect.map(_.take(n)).run
            } yield assert(takeListResult.succeeded)(isTrue) implies assert(takeStreamResult)(equalTo(takeListResult))
          }),
          testM("take short circuits")(
            for {
              ran    <- Ref.make(false)
              stream = (ZStream(1) ++ ZStream.fromEffect(ran.set(true)).drain).take(0)
              _      <- stream.runDrain
              result <- ran.get
            } yield assert(result)(isFalse)
          ),
          testM("take(0) short circuits")(
            for {
              units <- ZStream.never.take(0).runCollect
            } yield assert(units)(equalTo(Nil))
          ),
          testM("take(1) short circuits")(
            for {
              ints <- (ZStream(1) ++ ZStream.never).take(1).runCollect
            } yield assert(ints)(equalTo(List(1)))
          )
        ),
        testM("takeUntil") {
          checkM(streamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
            for {
              streamTakeUntil <- s.takeUntil(p).runCollect.run
              listTakeUntil   <- s.runCollect.map(as => as.takeWhile(!p(_)) ++ as.dropWhile(!p(_)).take(1)).run
            } yield assert(listTakeUntil.succeeded)(isTrue) implies assert(streamTakeUntil)(equalTo(listTakeUntil))
          }
        },
        suite("takeWhile")(
          testM("takeWhile")(checkM(streamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
            for {
              streamTakeWhile <- s.takeWhile(p).runCollect.run
              listTakeWhile   <- s.runCollect.map(_.takeWhile(p)).run
            } yield assert(listTakeWhile.succeeded)(isTrue) implies assert(streamTakeWhile)(equalTo(listTakeWhile))
          }),
          testM("takeWhile short circuits")(
            assertM(
              (ZStream(1) ++ ZStream.fail("Ouch"))
                .takeWhile(_ => false)
                .runDrain
                .either
            )(isRight(isUnit))
          )
        ),
        testM("tap") {
          for {
            ref <- Ref.make(0)
            res <- ZStream(1, 1).tap[Any, Nothing](a => ref.update(_ + a)).runCollect
            sum <- ref.get
          } yield assert(res)(equalTo(List(1, 1))) && assert(sum)(equalTo(2))
        },
        suite("throttleEnforce")(
          testM("free elements") {
            assertM(
              ZStream(1, 2, 3, 4)
                .throttleEnforce(0, Duration.Infinity)(_ => 0)
                .runCollect
            )(equalTo(List(1, 2, 3, 4)))
          },
          testM("no bandwidth") {
            assertM(
              ZStream(1, 2, 3, 4)
                .throttleEnforce(0, Duration.Infinity)(_ => 1)
                .runCollect
            )(equalTo(Nil))
          }
        ),
        suite("throttleShape")(
          testM("throttleShape") {
            for {
              fiber <- Queue
                        .bounded[Int](10)
                        .flatMap { queue =>
                          ZStream.fromQueue(queue).throttleShape(1, 1.second)(_.fold(0)(_ + _).toLong).process.use {
                            pull =>
                              for {
                                _    <- queue.offer(1)
                                res1 <- pull
                                _    <- queue.offer(2)
                                res2 <- pull
                                _    <- clock.sleep(4.seconds)
                                _    <- queue.offer(3)
                                res3 <- pull
                              } yield assert(List(res1, res2, res3))(equalTo(List(Chunk(1), Chunk(2), Chunk(3))))
                          }
                        }
                        .fork
              _    <- TestClock.adjust(8.seconds)
              test <- fiber.join
            } yield test
          },
          testM("infinite bandwidth") {
            Queue.bounded[Int](10).flatMap { queue =>
              ZStream.fromQueue(queue).throttleShape(1, 0.seconds)(_ => 100000L).process.use { pull =>
                for {
                  _       <- queue.offer(1)
                  res1    <- pull
                  _       <- queue.offer(2)
                  res2    <- pull
                  elapsed <- clock.currentTime(TimeUnit.SECONDS)
                } yield assert(elapsed)(equalTo(0L)) && assert(List(res1, res2))(equalTo(List(Chunk(1), Chunk(2))))
              }
            }
          },
          testM("with burst") {
            for {
              fiber <- Queue
                        .bounded[Int](10)
                        .flatMap { queue =>
                          ZStream.fromQueue(queue).throttleShape(1, 1.second, 2)(_.fold(0)(_ + _).toLong).process.use {
                            pull =>
                              for {
                                _    <- queue.offer(1)
                                res1 <- pull
                                _    <- TestClock.adjust(2.seconds)
                                _    <- queue.offer(2)
                                res2 <- pull
                                _    <- TestClock.adjust(4.seconds)
                                _    <- queue.offer(3)
                                res3 <- pull
                              } yield assert(List(res1, res2, res3))(equalTo(List(Chunk(1), Chunk(2), Chunk(3))))
                          }
                        }
                        .fork
              test <- fiber.join
            } yield test
          },
          testM("free elements") {
            assertM(
              ZStream(1, 2, 3, 4)
                .throttleShape(1, Duration.Infinity)(_ => 0)
                .runCollect
            )(equalTo(List(1, 2, 3, 4)))
          }
        ),
        suite("debounce")(
          testM("should drop earlier chunks within waitTime") {
            assertWithChunkCoordination(List(Chunk(1), Chunk(3, 4), Chunk(5), Chunk(6, 7))) {
              c =>
                val stream = ZStream
                  .fromQueue(c.queue)
                  .collectWhileSuccess
                  .debounce(1.second)
                  .tap(_ => c.proceed)

                assertM(for {
                  fiber  <- stream.runCollect.fork
                  _      <- c.offer.fork
                  _      <- (clock.sleep(500.millis) *> c.offer).fork
                  _      <- (clock.sleep(2.seconds) *> c.offer).fork
                  _      <- (clock.sleep(2500.millis) *> c.offer).fork
                  _      <- TestClock.adjust(3500.millis)
                  result <- fiber.join
                } yield result)(equalTo(List(Chunk(3, 4), Chunk(6, 7))))
            }
          },
          testM("should take latest chunk within waitTime") {
            assertWithChunkCoordination(List(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6))) { c =>
              val stream = ZStream
                .fromQueue(c.queue)
                .collectWhileSuccess
                .debounce(1.second)
                .tap(_ => c.proceed)

              assertM(for {
                fiber  <- stream.runCollect.fork
                _      <- c.offer *> c.offer *> c.offer
                _      <- TestClock.adjust(1.second)
                result <- fiber.join
              } yield result)(equalTo(List(Chunk(5, 6))))
            }
          },
          testM("should work properly with parallelization") {
            assertWithChunkCoordination(List(Chunk(1), Chunk(2), Chunk(3))) { c =>
              val stream = ZStream
                .fromQueue(c.queue)
                .collectWhileSuccess
                .debounce(1.second)
                .tap(_ => c.proceed)

              assertM(for {
                fiber  <- stream.runCollect.fork
                _      <- ZIO.collectAllPar_(List(c.offer, c.offer, c.offer))
                _      <- TestClock.adjust(1.second)
                result <- fiber.join
              } yield result)(hasSize(equalTo(1)))
            }
          },
          testM("should fail immediately") {
            val stream = ZStream.fromEffect(IO.fail(None)).debounce(Duration.Infinity)
            assertM(stream.runCollect.either)(isLeft(equalTo(None)))
          },
          testM("should work with empty streams") {
            val stream = ZStream.empty.debounce(5.seconds)
            assertM(stream.runCollect)(isEmpty)
          }
        ),
        suite("timeout")(
          testM("succeed") {
            assertM(
              ZStream
                .succeed(1)
                .timeout(Duration.Infinity)
                .runCollect
            )(equalTo(List(1)))
          },
          testM("should end stream") {
            assertM(
              ZStream
                .range(0, 5)
                .tap(_ => ZIO.sleep(Duration.Infinity))
                .timeout(Duration.Zero)
                .runCollect
            )(isEmpty)
          }
        ),
        testM("timeoutError") {
          assertM(
            ZStream
              .range(0, 5)
              .tap(_ => ZIO.sleep(Duration.Infinity))
              .timeoutError(false)(Duration.Zero)
              .runDrain
              .map(_ => true)
              .either
              .map(_.merge)
          )(isFalse)
        },
        testM("timeoutErrorCause") {
          val throwable = new Exception("BOOM")
          assertM(
            ZStream
              .range(0, 5)
              .tap(_ => ZIO.sleep(Duration.Infinity))
              .timeoutErrorCause(Cause.die(throwable))(Duration.Zero)
              .runDrain
              .sandbox
              .either
          )(equalTo(Left(Cause.Die(throwable))))
        },
        suite("timeoutTo")(
          testM("succeed") {
            assertM(
              ZStream
                .range(0, 5)
                .timeoutTo(Duration.Infinity)(ZStream.succeed(-1))
                .runCollect
            )(equalTo(List(0, 1, 2, 3, 4)))
          },
          testM("should switch stream") {
            assertWithChunkCoordination(List(Chunk(1), Chunk(2), Chunk(3))) { c =>
              assertM(
                for {
                  fiber <- ZStream
                            .fromQueue(c.queue)
                            .collectWhileSuccess
                            .flattenChunks
                            .timeoutTo(2.seconds)(ZStream.succeed(4))
                            .tap(_ => c.proceed)
                            .runCollect
                            .fork
                  _      <- c.offer *> TestClock.adjust(1.seconds) *> c.awaitNext
                  _      <- c.offer *> TestClock.adjust(3.seconds) *> c.awaitNext
                  _      <- c.offer
                  result <- fiber.join
                } yield result
              )(equalTo(List(1, 2, 4)))
            }
          },
          testM("should not apply timeout after switch") {
            for {
              queue1  <- Queue.unbounded[Int]
              queue2  <- Queue.unbounded[Int]
              stream1 = ZStream.fromQueue(queue1)
              stream2 = ZStream.fromQueue(queue2)
              fiber   <- stream1.timeoutTo(2.seconds)(stream2).runCollect.fork
              _       <- queue1.offer(1) *> TestClock.adjust(1.second)
              _       <- queue1.offer(2) *> TestClock.adjust(3.second)
              _       <- queue1.offer(3)
              _       <- queue2.offer(4) *> TestClock.adjust(3.second)
              _       <- queue2.offer(5) *> queue2.shutdown
              result  <- fiber.join
            } yield assert(result)(equalTo(List(1, 2, 4, 5)))
          }
        ),
        suite("toInputStream")(
          testM("read one-by-one") {
            checkM(tinyListOf(Gen.chunkOf(Gen.anyByte))) { chunks =>
              val content = chunks.flatMap(_.toList)
              ZStream.fromChunks(chunks: _*).toInputStream.use[Any, Throwable, TestResult] { is =>
                ZIO.succeedNow(
                  assert(Iterator.continually(is.read()).takeWhile(_ != -1).map(_.toByte).toList)(equalTo(content))
                )
              }
            }
          },
          testM("read in batches") {
            checkM(tinyListOf(Gen.chunkOf(Gen.anyByte))) {
              chunks =>
                val content = chunks.flatMap(_.toList)
                ZStream.fromChunks(chunks: _*).toInputStream.use[Any, Throwable, TestResult] { is =>
                  val batches: List[(Array[Byte], Int)] = Iterator.continually {
                    val buf = new Array[Byte](10)
                    val res = is.read(buf, 0, 4)
                    (buf, res)
                  }.takeWhile(_._2 != -1).toList
                  val combined = batches.flatMap { case (buf, size) => buf.take(size) }
                  ZIO.succeedNow(assert(combined)(equalTo(content)))
                }
            }
          },
          testM("`available` returns the size of chunk's leftover") {
            ZStream
              .fromIterable((1 to 10).map(_.toByte))
              .chunkN(3)
              .toInputStream
              .use[Any, Throwable, TestResult](is =>
                ZIO.effect {
                  val cold = is.available()
                  is.read()
                  val at1 = is.available()
                  is.read(new Array[Byte](2))
                  val at3 = is.available()
                  is.read()
                  val at4 = is.available()
                  List(
                    assert(cold)(equalTo(0)),
                    assert(at1)(equalTo(2)),
                    assert(at3)(equalTo(0)),
                    assert(at4)(equalTo(2))
                  ).reduce(_ && _)
                }
              )
          }
        ),
        testM("toIterator") {
          (for {
            counter  <- Ref.make(0).toManaged_ //Increment and get the value
            effect   = counter.updateAndGet(_ + 1)
            iterator <- ZStream.repeatEffect(effect).toIterator
            n        = 2000
            out <- ZStream
                    .fromIterator(iterator.map(_.merge))
                    .mapConcatM(element => effect.map(newElement => List(element, newElement)))
                    .take(n.toLong)
                    .runCollect
                    .toManaged_
          } yield assert(out)(equalTo((1 to n).toList))).use(ZIO.succeed(_))
        } @@ TestAspect.jvmOnly, // Until #3360 is solved
        suite("toQueue")(
          testM("toQueue")(checkM(Gen.chunkOfBounded(0, 3)(Gen.anyInt)) { (c: Chunk[Int]) =>
            val s = ZStream.fromChunk(c).flatMap(ZStream.succeed(_))
            assertM(
              s.toQueue(1000)
                .use(queue => queue.size.repeat(Schedule.doWhile(_ != c.size + 1)) *> queue.takeAll)
            )(
              equalTo(c.toSeq.toList.map(i => Exit.succeed(Chunk(i))) :+ Exit.fail(None))
            )
          }),
          testM("toQueueUnbounded")(checkM(Gen.chunkOfBounded(0, 3)(Gen.anyInt)) { (c: Chunk[Int]) =>
            val s = ZStream.fromChunk(c).flatMap(ZStream.succeed(_))
            assertM(
              s.toQueueUnbounded.use(queue => queue.size.repeat(Schedule.doWhile(_ != c.size + 1)) *> queue.takeAll)
            )(
              equalTo(c.toSeq.toList.map(i => Exit.succeed(Chunk(i))) :+ Exit.fail(None))
            )
          })
        ),
        suite("zipWith")(
          testM("zip doesn't pull too much when one of the streams is done") {
            val l = ZStream.fromChunks(Chunk(1, 2), Chunk(3, 4), Chunk(5)) ++ ZStream.fail("Nothing to see here")
            val r = ZStream.fromChunks(Chunk("a", "b"), Chunk("c"))
            assertM(l.zip(r).runCollect)(equalTo(List((1, "a"), (2, "b"), (3, "c"))))
          },
          testM("zip equivalence with Chunk#zipWith") {
            checkM(
              tinyListOf(Gen.chunkOf(Gen.anyInt)),
              tinyListOf(Gen.chunkOf(Gen.anyInt))
            ) { (l, r) =>
              val expected = Chunk.fromIterable(l).flatten.zip(Chunk.fromIterable(r).flatten)
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
              tinyListOf(Gen.chunkOf(Gen.anyInt).filter(_.size > 0)),
              tinyListOf(Gen.chunkOf(Gen.anyInt).filter(_.size > 0))
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
        }),
        testM("zipWithLatest") {
          import zio.test.environment.TestClock

          val s1 = ZStream.iterate(0)(_ + 1).fixed(100.millis)
          val s2 = ZStream.iterate(0)(_ + 1).fixed(70.millis)
          val s3 = s1.zipWithLatest(s2)((_, _))

          for {
            fiber <- s3.take(4).runCollect.fork
            _     <- TestClock.setTime(210.milliseconds)
            value <- fiber.join
          } yield assert(value)(equalTo(List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2)))
        }
      ),
      suite("Constructors")(
        testM("access") {
          for {
            result <- ZStream.access[String](identity).provide("test").runCollect.map(_.head)
          } yield assert(result)(equalTo("test"))
        },
        suite("accessM")(
          testM("accessM") {
            for {
              result <- ZStream.accessM[String](ZIO.succeedNow).provide("test").runCollect.map(_.head)
            } yield assert(result)(equalTo("test"))
          },
          testM("accessM fails") {
            for {
              result <- ZStream.accessM[Int](_ => ZIO.fail("fail")).provide(0).runCollect.run
            } yield assert(result)(fails(equalTo("fail")))
          }
        ),
        suite("accessStream")(
          testM("accessStream") {
            for {
              result <- ZStream.accessStream[String](ZStream.succeed(_)).provide("test").runCollect.map(_.head)
            } yield assert(result)(equalTo("test"))
          },
          testM("accessStream fails") {
            for {
              result <- ZStream.accessStream[Int](_ => ZStream.fail("fail")).provide(0).runCollect.run
            } yield assert(result)(fails(equalTo("fail")))
          }
        ),
        testM("chunkN") {
          checkM(tinyListOf(Gen.chunkOf(Gen.anyInt)) <*> (Gen.int(1, 100))) {
            case (list, n) =>
              val expected = list.flatten.grouped(n).toList
              assertM(
                ZStream
                  .fromChunks(list: _*)
                  .chunkN(n)
                  .mapChunks(ch => Chunk(ch.toList))
                  .runCollect
              )(equalTo(expected))
          }
        },
        testM("concatAll") {
          checkM(tinyListOf(Gen.chunkOf(Gen.anyInt))) { chunks =>
            assertM(ZStream.concatAll(Chunk.fromIterable(chunks.map(ZStream.fromChunk(_)))).runCollect)(
              equalTo(Chunk.fromIterable(chunks).flatten.toList)
            )
          }
        },
        testM("environment") {
          for {
            result <- ZStream.environment[String].provide("test").runCollect.map(_.head)
          } yield assert(result)(equalTo("test"))
        },
        suite("finalizer")(
          testM("happy path") {
            for {
              log <- Ref.make[List[String]](Nil)
              _ <- (for {
                    _ <- ZStream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                    _ <- ZStream.finalizer(log.update("Use" :: _))
                  } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
              execution <- log.get
            } yield assert(execution)(equalTo(List("Ensuring", "Release", "Use", "Acquire")))
          },
          testM("finalizer is not run if stream is not pulled") {
            for {
              ref <- Ref.make(false)
              _   <- ZStream.finalizer(ref.set(true)).process.use(_ => UIO.unit)
              fin <- ref.get
            } yield assert(fin)(isFalse)
          }
        ),
        testM("fromChunk") {
          checkM(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))(c =>
            assertM(ZStream.fromChunk(c).runCollect)(equalTo(c.toList))
          )
        },
        suite("fromChunks")(
          testM("fromChunks") {
            checkM(tinyListOf(Gen.chunkOf(Gen.anyInt))) { cs =>
              assertM(ZStream.fromChunks(cs: _*).runCollect)(equalTo(Chunk.fromIterable(cs).flatten.toList))
            }
          },
          testM("discards empty chunks") {
            ZStream.fromChunks(Chunk(1), Chunk.empty, Chunk(1)).process.use { pull =>
              assertM(nPulls(pull, 3))(equalTo(List(Right(Chunk(1)), Right(Chunk(1)), Left(None))))
            }
          }
        ),
        suite("fromEffectOption")(
          testM("emit one element with success") {
            val fa: ZIO[Any, Option[Int], Int] = ZIO.succeed(5)
            assertM(ZStream.fromEffectOption(fa).runCollect)(equalTo(List(5)))
          },
          testM("emit one element with failure") {
            val fa: ZIO[Any, Option[Int], Int] = ZIO.fail(Some(5))
            assertM(ZStream.fromEffectOption(fa).runCollect.either)(isLeft(equalTo(5)))
          } @@ zioTag(errors),
          testM("do not emit any element") {
            val fa: ZIO[Any, Option[Int], Int] = ZIO.fail(None)
            assertM(ZStream.fromEffectOption(fa).runCollect)(equalTo(List()))
          }
        ),
        testM("fromInputStream") {
          import java.io.ByteArrayInputStream
          val chunkSize = ZStream.DefaultChunkSize
          val data      = Array.tabulate[Byte](chunkSize * 5 / 2)(_.toByte)
          def is        = new ByteArrayInputStream(data)
          ZStream.fromInputStream(is, chunkSize).runCollect map { bytes => assert(bytes.toArray)(equalTo(data)) }
        },
        testM("fromIterable")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
          def lazyL = l
          assertM(ZStream.fromIterable(lazyL).runCollect)(equalTo(l))
        }),
        testM("fromIterableM")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
          assertM(ZStream.fromIterableM(UIO.effectTotal(l)).runCollect)(equalTo(l))
        }),
        testM("fromIterator")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
          def lazyIt = l.iterator
          assertM(ZStream.fromIterator(lazyIt).runCollect)(equalTo(l))
        }),
        testM("fromIteratorTotal")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
          def lazyIt = l.iterator
          assertM(ZStream.fromIteratorTotal(lazyIt).runCollect)(equalTo(l))
        }),
        suite("fromIteratorManaged")(
          testM("is safe to pull again after success") {
            for {
              ref <- Ref.make(false)
              pulls <- ZStream
                        .fromIteratorManaged(Managed.make(UIO.succeedNow(List(1, 2).iterator))(_ => ref.set(true)))
                        .process
                        .use(nPulls(_, 4))
              fin <- ref.get
            } yield assert(fin)(isTrue) && assert(pulls)(
              equalTo(List(Right(Chunk(1)), Right(Chunk(2)), Left(None), Left(None)))
            )
          },
          testM("is safe to pull again after failed acquisition") {
            val ex = new Exception("Ouch")

            for {
              ref <- Ref.make(false)
              pulls <- ZStream
                        .fromIteratorManaged(Managed.make(IO.fail(ex))(_ => ref.set(true)))
                        .process
                        .use(nPulls(_, 3))
              fin <- ref.get
            } yield assert(fin)(isFalse) && assert(pulls)(equalTo(List(Left(Some(ex)), Left(None), Left(None))))
          },
          testM("is safe to pull again after inner failure") {
            val ex = new Exception("Ouch")
            for {
              ref <- Ref.make(false)
              pulls <- ZStream
                        .fromIteratorManaged(Managed.make(UIO.succeedNow(List(1, 2).iterator))(_ => ref.set(true)))
                        .flatMap(n =>
                          ZStream.succeed((n * 2).toString) ++ ZStream.fail(ex) ++ ZStream.succeed(
                            (n * 3).toString
                          )
                        )
                        .process
                        .use(nPulls(_, 8))
              fin <- ref.get
            } yield assert(fin)(isTrue) && assert(pulls)(
              equalTo(
                List(
                  Right(Chunk("2")),
                  Left(Some(ex)),
                  Right(Chunk("3")),
                  Right(Chunk("4")),
                  Left(Some(ex)),
                  Right(Chunk("6")),
                  Left(None),
                  Left(None)
                )
              )
            )
          },
          testM("is safe to pull again from a failed Managed") {
            val ex = new Exception("Ouch")
            ZStream
              .fromIteratorManaged(Managed.fail(ex))
              .process
              .use(nPulls(_, 3))
              .map(assert(_)(equalTo(List(Left(Some(ex)), Left(None), Left(None)))))
          }
        ),
        testM("fromSchedule") {
          val schedule = Schedule.exponential(1.second) <* Schedule.recurs(5)
          val stream   = ZStream.fromSchedule(schedule)
          val zio = for {
            fiber <- stream.runCollect.fork
            _     <- TestClock.adjust(62.seconds)
            value <- fiber.join
          } yield value
          val expected = List(1.seconds, 2.seconds, 4.seconds, 8.seconds, 16.seconds, 32.seconds)
          assertM(zio)(equalTo(expected))
        },
        testM("fromQueue") {
          assertWithChunkCoordination(List(Chunk(1, 2))) { c =>
            assertM(for {
              fiber <- ZStream
                        .fromQueue(c.queue)
                        .collectWhileSuccess
                        .flattenChunks
                        .tap(_ => c.proceed)
                        .runCollect
                        .fork
              _      <- c.offer
              result <- fiber.join
            } yield result)(equalTo(List(1, 2)))
          }
        },
        testM("fromTQueue") {
          TQueue.bounded[Int](5).commit.flatMap {
            tqueue =>
              ZStream.fromTQueue(tqueue).toQueueUnbounded.use { queue =>
                for {
                  _      <- tqueue.offerAll(List(1, 2, 3)).commit
                  first  <- ZStream.fromQueue(queue).take(3).runCollect
                  _      <- tqueue.offerAll(List(4, 5)).commit
                  second <- ZStream.fromQueue(queue).take(2).runCollect
                } yield assert(first)(equalTo(List(1, 2, 3).map(i => Exit.succeed(Chunk.single(i))))) &&
                  assert(second)(equalTo(List(4, 5).map(i => Exit.succeed(Chunk.single(i)))))
              }
          }
        } @@ flaky,
        testM("iterate")(
          assertM(ZStream.iterate(1)(_ + 1).take(10).runCollect)(equalTo((1 to 10).toList))
        ),
        testM("paginate") {
          val s = (0, List(1, 2, 3))

          ZStream
            .paginate(s) {
              case (x, Nil)      => x -> None
              case (x, x0 :: xs) => x -> Some(x0 -> xs)
            }
            .runCollect
            .map(assert(_)(equalTo(List(0, 1, 2, 3))))
        },
        testM("paginateM") {
          val s = (0, List(1, 2, 3))

          assertM(
            ZStream
              .paginateM(s) {
                case (x, Nil)      => ZIO.succeed(x -> None)
                case (x, x0 :: xs) => ZIO.succeed(x -> Some(x0 -> xs))
              }
              .runCollect
          )(equalTo(List(0, 1, 2, 3)))
        },
        testM("range") {
          assertM(ZStream.range(0, 10).runCollect)(equalTo(Range(0, 10).toList))
        },
        testM("repeatEffect")(
          assertM(
            ZStream
              .repeatEffect(IO.succeed(1))
              .take(2)
              .runCollect
          )(equalTo(List(1, 1)))
        ),
        suite("repeatEffectOption")(
          testM("emit elements")(
            assertM(
              ZStream
                .repeatEffectOption(IO.succeed(1))
                .take(2)
                .runCollect
            )(equalTo(List(1, 1)))
          ),
          testM("emit elements until pull fails with None")(
            for {
              ref <- Ref.make(0)
              fa = for {
                newCount <- ref.updateAndGet(_ + 1)
                res      <- if (newCount >= 5) ZIO.fail(None) else ZIO.succeed(newCount)
              } yield res
              res <- ZStream
                      .repeatEffectOption(fa)
                      .take(10)
                      .runCollect
            } yield assert(res)(equalTo(List(1, 2, 3, 4)))
          )
        ),
        testM("repeatEffectWith")(
          for {
            ref <- Ref.make[List[Int]](Nil)
            fiber <- ZStream
                      .repeatEffectWith(ref.update(1 :: _), Schedule.spaced(10.millis))
                      .take(2)
                      .runDrain
                      .fork
            _      <- TestClock.adjust(50.millis)
            _      <- fiber.join
            result <- ref.get
          } yield assert(result)(equalTo(List(1, 1)))
        ),
        testM("unfold") {
          assertM(
            ZStream
              .unfold(0) { i =>
                if (i < 10) Some((i, i + 1))
                else None
              }
              .runCollect
          )(equalTo((0 to 9).toList))
        },
        testM("unfoldM") {
          assertM(
            ZStream
              .unfoldM(0) { i =>
                if (i < 10) IO.succeed(Some((i, i + 1)))
                else IO.succeed(None)
              }
              .runCollect
          )(equalTo((0 to 9).toList))
        }
      )
    ) @@ TestAspect.timed

  trait ChunkCoordination[A] {
    def queue: Queue[Take[Nothing, A]]
    def offer: UIO[Boolean]
    def proceed: UIO[Unit]
    def awaitNext: UIO[Unit]
  }

  def assertWithChunkCoordination[A](
    chunks: List[Chunk[A]]
  )(assertion: ChunkCoordination[A] => ZIO[Clock with TestClock, Nothing, TestResult]) =
    for {
      q  <- Queue.unbounded[Take[Nothing, A]]
      ps <- Queue.unbounded[Unit]
      ref <- Ref
              .make[List[List[Take[Nothing, A]]]](
                chunks.init.map { chunk =>
                  List(Exit.succeed(chunk))
                } ++ chunks.lastOption.map(chunk => List(Exit.succeed(chunk), Take.End))
              )
      chunkCoordination = new ChunkCoordination[A] {
        val queue = q
        val offer = ref.modify {
          case x :: xs => (x, xs)
          case Nil     => (Nil, Nil)
        }.flatMap(queue.offerAll)
        val proceed   = ps.offer(()).unit
        val awaitNext = ps.take
      }
      testResult <- assertion(chunkCoordination)
    } yield testResult
}
