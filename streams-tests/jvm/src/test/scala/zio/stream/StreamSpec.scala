package zio.stream

import scala.{ Stream => _ }

import StreamUtils._

import zio.Exit.Success
import zio.ZQueueSpecUtil.waitForSize
import zio._
import zio.duration._
import zio.random.Random
import zio.stm.TQueue
import zio.test.Assertion.{
  dies,
  equalTo,
  fails,
  hasSameElements,
  isFalse,
  isGreaterThan,
  isLeft,
  isNonEmptyString,
  isRight,
  isTrue,
  isUnit,
  startsWith
}
import zio.test.TestAspect.flaky
import zio.test._
import zio.test.environment.{ Live, TestClock }

object StreamSpec extends ZIOBaseSpec {

  import ZIOTag._

  def smallChunks[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] =
    Gen.small(Gen.chunkOfN(_)(a))

  def spec = suite("StreamSpec")(
    suite("Stream.absolve")(
      testM("happy path")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { xs =>
        val stream = ZStream.fromIterable(xs.map(Right(_)))
        assertM(stream.absolve.runCollect)(equalTo(xs))
      }),
      testM("failure")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { xs =>
        val stream = ZStream.fromIterable(xs.map(Right(_))) ++ ZStream.succeed(Left("Ouch"))
        assertM(stream.absolve.runCollect.run)(fails(equalTo("Ouch")))
      }) @@ zioTag(errors),
      testM("round-trip #1")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt)), Gen.anyString) { (xs, s) =>
        val xss    = ZStream.fromIterable(xs.map(Right(_)))
        val stream = xss ++ ZStream(Left(s)) ++ xss
        for {
          res1 <- stream.runCollect
          res2 <- stream.absolve.either.runCollect
        } yield assert(res1)(startsWith(res2))
      }),
      testM("round-trip #2")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt)), Gen.anyString) { (xs, s) =>
        val xss    = ZStream.fromIterable(xs)
        val stream = xss ++ ZStream.fail(s)
        for {
          res1 <- stream.runCollect.run
          res2 <- stream.either.absolve.runCollect.run
        } yield assert(res1)(fails(equalTo(s))) && assert(res2)(fails(equalTo(s)))
      })
    ),
    testM("Stream.access") {
      for {
        result <- ZStream.access[String](identity).provide("test").runHead.get
      } yield assert(result)(equalTo("test"))
    },
    suite("Stream.accessM")(
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
    suite("Stream.accessStream")(
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
    suite("Stream.aggregateAsync")(
      testM("aggregateAsync") {
        Stream(1, 1, 1, 1)
          .aggregateAsync(ZSink.foldUntil(List[Int](), 3)((acc, el: Int) => el :: acc).map(_.reverse))
          .runCollect
          .map { result =>
            assert(result.flatten)(equalTo(List(1, 1, 1, 1))) &&
            assert(result.forall(_.length <= 3))(isTrue)
          }
      },
      testM("error propagation") {
        val e = new RuntimeException("Boom")
        assertM(
          Stream(1, 1, 1, 1)
            .aggregateAsync(ZSink.die(e))
            .runCollect
            .run
        )(dies(equalTo(e)))
      } @@ zioTag(errors),
      testM("error propagation") {
        val e    = new RuntimeException("Boom")
        val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]())(_ => true)((_, _) => ZIO.die(e))

        assertM(
          Stream(1, 1)
            .aggregateAsync(sink)
            .runCollect
            .run
        )(dies(equalTo(e)))
      } @@ zioTag(errors),
      testM("interruption propagation") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          cancelled <- Ref.make(false)
          sink = ZSink.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
            if (el == 1) UIO.succeed((el :: acc, Chunk[Int]()))
            else
              (latch.succeed(()) *> ZIO.infinity)
                .onInterrupt(cancelled.set(true))
          }
          fiber  <- Stream(1, 1, 2).aggregateAsync(sink).runCollect.untraced.fork
          _      <- latch.await
          _      <- fiber.interrupt
          result <- cancelled.get
        } yield assert(result)(isTrue)
      } @@ zioTag(interruption),
      testM("interruption propagation") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          cancelled <- Ref.make(false)
          sink = ZSink.fromEffect {
            (latch.succeed(()) *> ZIO.infinity)
              .onInterrupt(cancelled.set(true))
          }
          fiber  <- Stream(1, 1, 2).aggregateAsync(sink).runCollect.untraced.fork
          _      <- latch.await
          _      <- fiber.interrupt
          result <- cancelled.get
        } yield assert(result)(isTrue)
      } @@ zioTag(interruption),
      testM("leftover handling") {
        val data = List(1, 2, 2, 3, 2, 3)
        assertM(
          Stream(data: _*)
            .aggregateAsync(
              Sink
                .foldWeighted(List[Int]())((i: Int) => i.toLong, 4)((acc, el) => el :: acc)
                .map(_.reverse)
            )
            .runCollect
            .map(_.flatten)
        )(equalTo(data))
      }
    ),
    suite("Stream.aggregateAsyncWithinEither")(
      testM("aggregateAsyncWithinEither") {
        for {
          result <- Stream(1, 1, 1, 1, 2)
                     .aggregateAsyncWithinEither(
                       Sink
                         .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                           if (el == 1) ((el :: acc._1, true), Chunk.empty)
                           else if (el == 2 && acc._1.isEmpty) ((el :: acc._1, false), Chunk.empty)
                           else ((acc._1, false), Chunk.single(el))
                         }
                         .map(_._1),
                       Schedule.spaced(30.minutes)
                     )
                     .runCollect
        } yield assert(result)(equalTo(List(Right(List(1, 1, 1, 1)), Right(List(2)))))
      },
      testM("error propagation") {
        val e = new RuntimeException("Boom")
        assertM(
          Stream(1, 1, 1, 1)
            .aggregateAsyncWithinEither(ZSink.die(e), Schedule.spaced(30.minutes))
            .runCollect
            .run
        )(dies(equalTo(e)))
      } @@ zioTag(errors),
      testM("error propagation") {
        val e    = new RuntimeException("Boom")
        val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]())(_ => true)((_, _) => ZIO.die(e))

        assertM(
          Stream(1, 1)
            .aggregateAsyncWithinEither(sink, Schedule.spaced(30.minutes))
            .runCollect
            .run
        )(dies(equalTo(e)))
      } @@ zioTag(errors),
      testM("interruption propagation") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          cancelled <- Ref.make(false)
          sink = ZSink.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
            if (el == 1) UIO.succeed((el :: acc, Chunk[Int]()))
            else
              (latch.succeed(()) *> ZIO.infinity)
                .onInterrupt(cancelled.set(true))
          }
          fiber <- Stream(1, 1, 2)
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
          sink = ZSink.fromEffect {
            (latch.succeed(()) *> ZIO.infinity)
              .onInterrupt(cancelled.set(true))
          }
          fiber <- Stream(1, 1, 2)
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
          Stream(data: _*)
            .aggregateAsyncWithinEither(
              Sink
                .foldWeighted(List[Int]())((i: Int) => i.toLong, 4)((acc, el) => el :: acc)
                .map(_.reverse),
              Schedule.spaced(100.millis)
            )
            .collect {
              case Right(v) => v
            }
            .runCollect
            .map(_.flatten)
        )(equalTo(data))
      }
    ),
    suite("Stream.aggregateAsyncWithin")(
      testM("aggregateAsyncWithin") {
        for {
          result <- Stream(1, 1, 1, 1, 2)
                     .aggregateAsyncWithin(
                       Sink
                         .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                           if (el == 1) ((el :: acc._1, true), Chunk.empty)
                           else if (el == 2 && acc._1.isEmpty) ((el :: acc._1, false), Chunk.empty)
                           else ((acc._1, false), Chunk.single(el))
                         }
                         .map(_._1),
                       Schedule.spaced(30.minutes)
                     )
                     .runCollect
        } yield assert(result)(equalTo(List(List(1, 1, 1, 1), List(2))))
      }
    ),
    suite("Stream.bracket")(
      testM("bracket")(
        for {
          done           <- Ref.make(false)
          iteratorStream = Stream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(Stream.fromIterable(_))
          result         <- iteratorStream.run(Sink.collectAll[Int])
          released       <- done.get
        } yield assert(result)(equalTo(List(0, 1, 2))) && assert(released)(isTrue)
      ),
      testM("bracket short circuits")(
        for {
          done <- Ref.make(false)
          iteratorStream = Stream
            .bracket(UIO(0 to 3))(_ => done.set(true))
            .flatMap(Stream.fromIterable(_))
            .take(2)
          result   <- iteratorStream.run(Sink.collectAll[Int])
          released <- done.get
        } yield assert(result)(equalTo(List(0, 1))) && assert(released)(isTrue)
      ),
      testM("no acquisition when short circuiting")(
        for {
          acquired       <- Ref.make(false)
          iteratorStream = (Stream(1) ++ Stream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
          _              <- iteratorStream.run(Sink.drain)
          result         <- acquired.get
        } yield assert(result)(isFalse)
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
        } yield assert(released)(isTrue)
      },
      testM("flatMap associativity doesn't affect bracket lifetime")(
        for {
          leftAssoc <- Stream
                        .bracket(Ref.make(true))(_.set(false))
                        .flatMap(Stream.succeed(_))
                        .flatMap(r => Stream.fromEffect(r.get))
                        .run(Sink.await[Boolean])
          rightAssoc <- Stream
                         .bracket(Ref.make(true))(_.set(false))
                         .flatMap(Stream.succeed(_).flatMap(r => Stream.fromEffect(r.get)))
                         .run(Sink.await[Boolean])
        } yield assert(leftAssoc -> rightAssoc)(equalTo(true -> true))
      )
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
                expected = Range(0, 5).toList
              } yield assert(out1)(equalTo(expected)) && assert(out2)(equalTo(expected))
            case _ =>
              UIO(assert(())(Assertion.nothing))
          }
      },
      testM("Errors") {
        (Stream.range(0, 1) ++ Stream.fail("Boom")).broadcast(2, 12).use {
          case s1 :: s2 :: Nil =>
            for {
              out1     <- s1.runCollect.either
              out2     <- s2.runCollect.either
              expected = Left("Boom")
            } yield assert(out1)(equalTo(expected)) && assert(out2)(equalTo(expected))
          case _ =>
            UIO(assert(())(Assertion.nothing))
        }
      } @@ zioTag(errors),
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
              } yield assert(snapshot1)(equalTo(List(2, 1, 0))) && assert(snapshot2)(
                equalTo(Range(0, 5).toList.reverse)
              )
            case _ =>
              UIO(assert(())(Assertion.nothing))
          }
      },
      testM("Unsubscribe") {
        Stream.range(0, 5).broadcast(2, 2).use {
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
    suite("Stream.catchAllCause")(
      testM("recovery from errors") {
        val s1 = Stream(1, 2) ++ Stream.fail("Boom")
        val s2 = Stream(3, 4)
        s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
      },
      testM("recovery from defects") {
        val s1 = Stream(1, 2) ++ Stream.dieMessage("Boom")
        val s2 = Stream(3, 4)
        s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4))))
      },
      testM("happy path") {
        val s1 = Stream(1, 2)
        val s2 = Stream(3, 4)
        s1.catchAllCause(_ => s2).runCollect.map(assert(_)(equalTo(List(1, 2))))
      },
      testM("executes finalizers") {
        for {
          fins   <- Ref.make(List[String]())
          s1     = (Stream(1, 2) ++ Stream.fail("Boom")).ensuring(fins.update("s1" :: _))
          s2     = (Stream(3, 4) ++ Stream.fail("Boom")).ensuring(fins.update("s2" :: _))
          _      <- s1.catchAllCause(_ => s2).runCollect.run
          result <- fins.get
        } yield assert(result)(equalTo(List("s2", "s1")))
      }
    ) @@ zioTag(errors),
    suite("Stream.chunkN")(
      testM("empty stream") {
        assertM(Stream.empty.chunkN(1).runCollect)(equalTo(Nil))
      },
      testM("non-positive chunk size") {
        assertM(Stream(1, 2, 3).chunkN(0).chunks.runCollect)(equalTo(List(Chunk(1), Chunk(2), Chunk(3))))
      },
      testM("full last chunk") {
        assertM(Stream(1, 2, 3, 4, 5, 6).chunkN(2).chunks.runCollect)(
          equalTo(List(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6)))
        )
      },
      testM("non-full last chunk") {
        assertM(Stream(1, 2, 3, 4, 5).chunkN(2).chunks.runCollect)(equalTo(List(Chunk(1, 2), Chunk(3, 4), Chunk(5))))
      },
      testM("error") {
        (Stream(1, 2, 3, 4, 5) ++ Stream.fail("broken"))
          .chunkN(3)
          .catchAll(_ => ZStreamChunk.succeed(Chunk(6)))
          .chunks
          .runCollect
          .map(assert(_)(equalTo(List(Chunk(1, 2, 3), Chunk(4, 5), Chunk(6)))))
      } @@ zioTag(errors)
    ),
    testM("Stream.collect") {
      assertM(Stream(Left(1), Right(2), Left(3)).collect {
        case Right(n) => n
      }.runCollect)(equalTo(List(2)))
    },
    suite("Stream.collectM")(
      testM("collectM") {
        assertM(
          Stream(Left(1), Right(2), Left(3))
            .collectM[Any, Throwable, Int] {
              case Right(n) => ZIO(n * 2)
            }
            .runCollect
        )(equalTo(List(4)))
      },
      testM("collectM fails") {
        assertM(
          Stream(Left(1), Right(2), Left(3))
            .collectM[Any, String, Int] {
              case Right(_) => ZIO.fail("Ouch")
            }
            .runDrain
            .either
        )(isLeft(isNonEmptyString))
      } @@ zioTag(errors)
    ),
    suite("Stream.collectWhile")(
      testM("collectWhile") {
        assertM(Stream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile {
          case Some(v) => v
        }.runCollect)(equalTo(List(1, 2, 3)))
      },
      testM("collectWhile short circuits") {
        assertM((Stream(Option(1)) ++ Stream.fail("Ouch")).collectWhile {
          case None => 1
        }.runDrain.either)(isRight(isUnit))
      }
    ),
    suite("Stream.collectWhileM")(
      testM("collectWhileM") {
        assertM(
          Stream(Some(1), Some(2), Some(3), None, Some(4))
            .collectWhileM[Any, Throwable, Int] {
              case Some(v) => ZIO(v * 2)
            }
            .runCollect
        )(equalTo(List(2, 4, 6)))
      },
      testM("collectWhileM short circuits") {
        assertM(
          (Stream(Option(1)) ++ Stream.fail("Ouch"))
            .collectWhileM[Any, String, Int] {
              case None => ZIO.succeed(1)
            }
            .runDrain
            .either
        )(isRight(isUnit))
      },
      testM("collectWhileM fails") {
        assertM(
          Stream(Some(1), Some(2), Some(3), None, Some(4))
            .collectWhileM[Any, String, Int] {
              case Some(_) => ZIO.fail("Ouch")
            }
            .runDrain
            .either
        )(isLeft(isNonEmptyString))
      } @@ zioTag(errors)
    ),
    suite("Stream.concat")(
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
          _ <- (Stream.finalizer(log.update("Second" :: _)) ++ Stream
                .finalizer(log.update("First" :: _))).runDrain
          execution <- log.get
        } yield assert(execution)(equalTo(List("First", "Second")))
      }
    ),
    suite("Stream.distributedWithDynamic")(
      testM("ensures no race between subscription and stream end") {

        val stream: ZStream[Any, Nothing, Either[Unit, Unit]] = ZStream.empty
        stream.distributedWithDynamic[Nothing, Either[Unit, Unit]](1, _ => UIO.succeed(_ => true)).use { add =>
          val subscribe = ZStream.unwrap(add.map {
            case (_, queue) =>
              ZStream.fromQueue(queue).unTake
          })
          Promise.make[Nothing, Unit].flatMap { onEnd =>
            subscribe.ensuring(onEnd.succeed(())).runDrain.fork *>
              onEnd.await *>
              subscribe.runDrain *>
              ZIO.succeed(assertCompletes)
          }
        }
      }
    ),
    testM("Stream.drain")(
      for {
        ref <- Ref.make(List[Int]())
        _   <- Stream.range(0, 10).mapM(i => ref.update(i :: _)).drain.run(Sink.drain)
        l   <- ref.get
      } yield assert(l.reverse)(equalTo(Range(0, 10).toList))
    ),
    suite("Stream.dropUntil")(testM("dropUntil") {
      checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
        for {
          res1 <- s.dropUntil(p).runCollect
          res2 <- s.runCollect.map(StreamUtils.dropUntil(_)(p))
        } yield assert(res1)(equalTo(res2))
      }
    }),
    suite("Stream.dropWhile")(
      testM("dropWhile")(
        checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s: Stream[String, Byte], p: Byte => Boolean) =>
          for {
            res1 <- s.dropWhile(p).runCollect
            res2 <- s.runCollect.map(_.dropWhile(p))
          } yield assert(res1)(equalTo(res2))
        }
      ),
      testM("short circuits") {
        assertM(
          (Stream(1) ++ Stream.fail("Ouch"))
            .take(1)
            .dropWhile(_ => true)
            .runDrain
            .either
        )(isRight(isUnit))
      }
    ),
    testM("Stream.either") {
      val s = Stream(1, 2, 3) ++ Stream.fail("Boom")
      s.either.runCollect.map(assert(_)(equalTo(List(Right(1), Right(2), Right(3), Left("Boom")))))
    },
    testM("Stream.ensuring") {
      for {
        log <- Ref.make[List[String]](Nil)
        _ <- (for {
              _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
              _ <- Stream.fromEffect(log.update("Use" :: _))
            } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
        execution <- log.get
      } yield assert(execution)(equalTo(List("Ensuring", "Release", "Use", "Acquire")))
    },
    testM("Stream.ensuringFirst") {
      for {
        log <- Ref.make[List[String]](Nil)
        _ <- (for {
              _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
              _ <- Stream.fromEffect(log.update("Use" :: _))
            } yield ()).ensuringFirst(log.update("Ensuring" :: _)).runDrain
        execution <- log.get
      } yield assert(execution)(equalTo(List("Release", "Ensuring", "Use", "Acquire")))
    },
    testM("Stream.environment") {
      for {
        result <- ZStream.environment[String].provide("test").runHead.get
      } yield assert(result)(equalTo("test"))
    },
    testM("Stream.filter")(checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
      for {
        res1 <- s.filter(p).runCollect
        res2 <- s.runCollect.map(_.filter(p))
      } yield assert(res1)(equalTo(res2))
    }),
    testM("Stream.filterM")(checkM(pureStreamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
      for {
        res1 <- s.filterM(s => IO.succeed(p(s))).runCollect
        res2 <- s.runCollect.map(_.filter(p))
      } yield assert(res1)(equalTo(res2))
    }),
    suite("Stream.finalizer")(
      testM("happy path") {
        for {
          log <- Ref.make[List[String]](Nil)
          _ <- (for {
                _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                _ <- Stream.finalizer(log.update("Use" :: _))
              } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
          execution <- log.get
        } yield assert(execution)(equalTo(List("Ensuring", "Release", "Use", "Acquire")))
      },
      testM("finalizer is not run if stream is not pulled") {
        for {
          ref <- Ref.make(false)
          _   <- Stream.finalizer(ref.set(true)).process.use(_ => UIO.unit)
          fin <- ref.get
        } yield assert(fin)(isFalse)
      }
    ),
    suite("Stream.flatMap")(
      testM("deep flatMap stack safety") {
        def fib(n: Int): Stream[Nothing, Int] =
          if (n <= 1) Stream.succeed(n)
          else
            fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => Stream.succeed(a + b)))

        val stream   = fib(20)
        val expected = 6765

        assertM(stream.runCollect)(equalTo(List(expected)))
      },
      testM("left identity")(checkM(Gen.anyInt, Gen.function(pureStreamOfInts)) { (x, f) =>
        for {
          res1 <- Stream(x).flatMap(f).runCollect
          res2 <- f(x).runCollect
        } yield assert(res1)(equalTo(res2))
      }),
      testM("right identity")(
        checkM(pureStreamOfInts)(m =>
          for {
            res1 <- m.flatMap(i => Stream(i)).runCollect
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
        } yield assert(result)(equalTo(List(3, 3, 2, 1, 1)))

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
        } yield assert(result)(equalTo(List(1, 2, 3, 4, 4, 4, 3, 2, 3, 4, 4, 4, 3, 2, 1).reverse))
      },
      testM("exit signal") {
        for {
          ref <- Ref.make(false)
          inner = Stream
            .bracketExit(UIO.unit)((_, e) =>
              e match {
                case Exit.Failure(_) => ref.set(true)
                case Exit.Success(_) => UIO.unit
              }
            )
            .flatMap(_ => Stream.fail("Ouch"))
          _   <- Stream.succeed(()).flatMap(_ => inner).runDrain.either.unit
          fin <- ref.get
        } yield assert(fin)(isTrue)
      }
    ),
    suite("Stream.flatMapPar / flattenPar / mergeAll")(
      testM("guarantee ordering")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { (m: List[Int]) =>
        for {
          flatMap    <- Stream.fromIterable(m).flatMap(i => Stream(i, i)).runCollect
          flatMapPar <- Stream.fromIterable(m).flatMapPar(1)(i => Stream(i, i)).runCollect
        } yield assert(flatMap)(equalTo(flatMapPar))
      }),
      testM("consistent with flatMap")(checkM(Gen.int(1, Int.MaxValue), Gen.small(Gen.listOfN(_)(Gen.anyInt))) {
        (n, m) =>
          for {
            flatMap    <- Stream.fromIterable(m).flatMap(i => Stream(i, i)).runCollect.map(_.toSet)
            flatMapPar <- Stream.fromIterable(m).flatMapPar(n)(i => Stream(i, i)).runCollect.map(_.toSet)
          } yield assert(n)(isGreaterThan(0)) implies assert(flatMap)(equalTo(flatMapPar))
      }),
      testM("short circuiting") {
        assertM(
          Stream
            .mergeAll(2)(
              Stream.never,
              Stream(1)
            )
            .take(1)
            .run(Sink.collectAll[Int])
        )(equalTo(List(1)))
      },
      testM("interruption propagation") {
        for {
          substreamCancelled <- Ref.make[Boolean](false)
          latch              <- Promise.make[Nothing, Unit]
          fiber <- Stream(())
                    .flatMapPar(1)(_ =>
                      ZStream.fromEffect(
                        (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                      )
                    )
                    .run(Sink.collectAll[Unit])
                    .fork
          _         <- latch.await
          _         <- fiber.interrupt
          cancelled <- substreamCancelled.get
        } yield assert(cancelled)(isTrue)
      } @@ zioTag(interruption),
      testM("inner errors interrupt all fibers") {
        for {
          substreamCancelled <- Ref.make[Boolean](false)
          latch              <- Promise.make[Nothing, Unit]
          result <- ZStream(
                     ZStream.fromEffect(
                       (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                     ),
                     Stream.fromEffect(latch.await *> ZIO.fail("Ouch"))
                   ).flatMapPar(2)(identity)
                     .run(Sink.drain)
                     .either
          cancelled <- substreamCancelled.get
        } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
      } @@ zioTag(errors, interruption),
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
                     .run(Sink.drain)
                     .either
          cancelled <- substreamCancelled.get
        } yield assert(cancelled)(isTrue) && assert(result)(isLeft(equalTo("Ouch")))
      } @@ zioTag(errors, interruption),
      testM("inner defects interrupt all fibers") {
        val ex = new RuntimeException("Ouch")

        for {
          substreamCancelled <- Ref.make[Boolean](false)
          latch              <- Promise.make[Nothing, Unit]
          result <- ZStream(
                     ZStream.fromEffect(
                       (latch.succeed(()) *> ZIO.infinity).onInterrupt(substreamCancelled.set(true))
                     ),
                     Stream.fromEffect(latch.await *> ZIO.die(ex))
                   ).flatMapPar(2)(identity)
                     .run(Sink.drain)
                     .run
          cancelled <- substreamCancelled.get
        } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
      } @@ zioTag(errors, interruption),
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
                     .run(Sink.drain)
                     .run
          cancelled <- substreamCancelled.get
        } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
      } @@ zioTag(errors, interruption),
      testM("finalizer ordering") {
        for {
          execution <- Ref.make[List[String]](Nil)
          inner = Stream
            .bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
          _ <- Stream
                .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ => execution.update("OuterRelease" :: _))
                .flatMapPar(2)(identity)
                .runDrain
          results <- execution.get
        } yield assert(results)(equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
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
                  else Stream.managed(semaphore.withPermitManaged).flatMap(_ => Stream.never)
                }
                .runDrain
          result <- semaphore.withPermit(lastExecuted.get)
        } yield assert(result)(isTrue)
      },
      testM("guarantee ordering with parallelism") {
        for {
          lastExecuted <- Ref.make(0)
          semaphore    <- Semaphore.make(4)
          _ <- Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .flatMapParSwitch(4) { i =>
                  if (i > 8)
                    Stream.bracket(UIO.unit)(_ => lastExecuted.update(_ + 1)).flatMap(_ => Stream.empty)
                  else Stream.managed(semaphore.withPermitManaged).flatMap(_ => Stream.never)
                }
                .runDrain
          result <- semaphore.withPermits(4)(lastExecuted.get)
        } yield assert(result)(equalTo(4))
      },
      testM("short circuiting") {
        assertM(
          Stream(Stream.never, Stream(1))
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
      } @@ zioTag(interruption) @@ flaky,
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
      } @@ zioTag(errors, interruption) @@ flaky,
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
      } @@ zioTag(errors, interruption),
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
                   ).flatMapParSwitch(2)(identity)
                     .run(Sink.drain)
                     .run
          cancelled <- substreamCancelled.get
        } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
      } @@ zioTag(errors, interruption),
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
                     .run(Sink.drain)
                     .run
          cancelled <- substreamCancelled.get
        } yield assert(cancelled)(isTrue) && assert(result)(dies(equalTo(ex)))
      } @@ zioTag(errors, interruption),
      testM("finalizer ordering") {
        for {
          execution <- Ref.make(List.empty[String])
          inner     = Stream.bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
          _ <- Stream
                .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ => execution.update("OuterRelease" :: _))
                .flatMapParSwitch(2)(identity)
                .runDrain
          results <- execution.get
        } yield assert(results)(equalTo(List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")))
      }
    ),
    suite("Stream.foreach/foreachWhile")(
      testM("foreach") {
        for {
          ref <- Ref.make(0)
          _   <- Stream(1, 1, 1, 1, 1).foreach[Any, Nothing](a => ref.update(_ + a))
          sum <- ref.get
        } yield assert(sum)(equalTo(5))
      },
      testM("foreachWhile") {
        for {
          ref <- Ref.make(0)
          _ <- Stream(1, 1, 1, 1, 1, 1).foreachWhile[Any, Nothing](a =>
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
          flag    <- Ref.make(true)
          _       <- (Stream(true, true, false) ++ Stream.fromEffect(flag.set(false)).drain).foreachWhile(ZIO.succeed(_))
          skipped <- flag.get
        } yield assert(skipped)(isTrue)
      }
    ),
    testM("Stream.forever") {
      for {
        ref <- Ref.make(0)
        _ <- Stream(1).forever.foreachWhile[Any, Nothing](_ =>
              ref.modify(sum => (if (sum >= 9) false else true, sum + 1))
            )
        sum <- ref.get
      } yield assert(sum)(equalTo(10))
    },
    testM("Stream.fromChunk")(checkM(smallChunks(Gen.anyInt)) { c =>
      assertM(Stream.fromChunk(c).runCollect)(equalTo(c.toSeq.toList))
    }),
    testM("Stream.fromInputStream") {
      import java.io.ByteArrayInputStream
      val chunkSize = ZStreamChunk.DefaultChunkSize
      val data      = Array.tabulate[Byte](chunkSize * 5 / 2)(_.toByte)
      def is        = new ByteArrayInputStream(data)
      ZStream.fromInputStream(is, chunkSize).run(Sink.collectAll[Chunk[Byte]]) map { chunks =>
        assert(chunks.flatMap(_.toArray[Byte]).toArray)(equalTo(data))
      }
    },
    testM("Stream.fromIterable")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
      def lazyL = l
      assertM(Stream.fromIterable(lazyL).runCollect)(equalTo(l))
    }),
    testM("Stream.fromIterableM")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
      assertM(Stream.fromIterableM(UIO.effectTotal(l)).runCollect)(equalTo(l))
    }),
    testM("Stream.fromIteratorTotal")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
      def lazyIt = l.iterator
      assertM(Stream.fromIteratorTotal(lazyIt).runCollect)(equalTo(l))
    }),
    testM("Stream.fromIterator")(checkM(Gen.small(Gen.listOfN(_)(Gen.anyInt))) { l =>
      def lazyIt = l.iterator
      assertM(Stream.fromIterator(lazyIt).runCollect)(equalTo(l))
    }),
    testM("Stream.fromQueue")(checkM(smallChunks(Gen.anyInt)) { c =>
      for {
        queue <- Queue.unbounded[Int]
        _     <- queue.offerAll(c.toSeq)
        fiber <- Stream
                  .fromQueue(queue)
                  .foldWhileM(List[Int]())(_ => true)((acc, el) => IO.succeed(el :: acc))
                  .map(_.reverse)
                  .fork
        _     <- waitForSize(queue, -1)
        _     <- queue.shutdown
        items <- fiber.join
      } yield assert(items)(equalTo(c.toSeq.toList))
    }),
    testM("Stream.fromSchedule") {
      val schedule = Schedule.exponential(1.second) <* Schedule.recurs(5)
      val stream   = ZStream.fromSchedule(schedule)
      val zio      = TestClock.adjust(62.seconds) *> stream.runCollect
      val expected = List(1.seconds, 2.seconds, 4.seconds, 8.seconds, 16.seconds, 32.seconds)
      assertM(zio)(equalTo(expected))
    },
    testM("Stream.fromTQueue") {
      TQueue.bounded[Int](5).commit.flatMap {
        tqueue =>
          ZStream.fromTQueue(tqueue).toQueueUnbounded.use { (queue: Queue[Take[Nothing, Int]]) =>
            for {
              _      <- tqueue.offerAll(List(1, 2, 3)).commit
              first  <- ZIO.collectAll(ZIO.replicate(3)(queue.take))
              _      <- tqueue.offerAll(List(4, 5)).commit
              second <- ZIO.collectAll(ZIO.replicate(2)(queue.take))
            } yield assert(first)(equalTo(List(1, 2, 3).map(Take.Value(_)))) &&
              assert(second)(equalTo(List(4, 5).map(Take.Value(_))))
          }
      }
    },
    suite("Stream.fromEffectOption")(
      testM("emit one element with success") {
        val fa: ZIO[Any, Option[Int], Int] = ZIO.succeed(5)
        assertM(Stream.fromEffectOption(fa).runCollect)(equalTo(List(5)))
      },
      testM("emit one element with failure") {
        val fa: ZIO[Any, Option[Int], Int] = ZIO.fail(Some(5))
        assertM(Stream.fromEffectOption(fa).runCollect.either)(isLeft(equalTo(5)))
      } @@ zioTag(errors),
      testM("do not emit any element") {
        val fa: ZIO[Any, Option[Int], Int] = ZIO.fail(None)
        assertM(Stream.fromEffectOption(fa).runCollect)(equalTo(List()))
      }
    ),
    suite("Stream.groupBy")(
      testM("values") {
        val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
        assertM(
          Stream
            .fromIterable(words)
            .groupByKey(identity, 8192) {
              case (k, s) =>
                s.aggregate(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
                  .take(1)
                  .map((k -> _))
            }
            .runCollect
            .map(_.toMap)
        )(equalTo((0 to 100).map((_.toString -> 1000)).toMap))
      },
      testM("first") {
        val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
        assertM(
          Stream
            .fromIterable(words)
            .groupByKey(identity, 1050)
            .first(2) {
              case (k, s) =>
                s.aggregate(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
                  .take(1)
                  .map((k -> _))
            }
            .runCollect
            .map(_.toMap)
        )(equalTo((0 to 1).map((_.toString -> 1000)).toMap))
      },
      testM("filter") {
        val words = List.fill(1000)(0 to 100).flatten
        assertM(
          Stream
            .fromIterable(words)
            .groupByKey(identity, 1050)
            .filter(_ <= 5) {
              case (k, s) =>
                s.aggregate(Sink.foldLeft[Int, Int](0) { case (acc, _) => acc + 1 })
                  .take(1)
                  .map((k -> _))
            }
            .runCollect
            .map(_.toMap)
        )(equalTo((0 to 5).map((_ -> 1000)).toMap))
      },
      testM("outer errors") {
        val words = List("abc", "test", "test", "foo")
        assertM(
          (Stream.fromIterable(words) ++ Stream.fail("Boom"))
            .groupByKey(identity) { case (_, s) => s.drain }
            .runCollect
            .either
        )(isLeft(equalTo("Boom")))
      } @@ zioTag(errors)
    ),
    testM("Stream.grouped")(
      assertM(Stream(1, 2, 3, 4).grouped(2).runCollect)(equalTo(List(List(1, 2), List(3, 4))))
    ),
    suite("Stream.groupedWithin")(
      testM("group before chunk size is reached due to time window") {
        Queue.bounded[Take[Nothing, Int]](8).flatMap {
          queue =>
            Ref
              .make[List[List[Take[Nothing, Int]]]](
                List(
                  List(Take.Value(1), Take.Value(2)),
                  List(Take.Value(3), Take.Value(4)),
                  List(Take.Value(5), Take.End)
                )
              )
              .flatMap { ref =>
                val offer = ref.modify {
                  case x :: xs => (x, xs)
                  case Nil     => (Nil, Nil)
                }.flatMap(queue.offerAll)
                val stream = ZStream.fromEffect(offer) *> ZStream
                  .fromQueue(queue)
                  .unTake
                  .tap(_ => TestClock.adjust(1.second))
                  .groupedWithin(10, 2.seconds)
                  .tap(_ => offer)
                assertM(stream.runCollect)(equalTo(List(List(1, 2), List(3, 4), List(5))))
              }
        }
      } @@ flaky,
      testM("group immediately when chunk size is reached") {
        assertM(ZStream(1, 2, 3, 4).groupedWithin(2, 10.seconds).runCollect)(equalTo(List(List(1, 2), List(3, 4))))
      }
    ),
    suite("Stream.runHead")(
      testM("nonempty stream")(
        assertM(Stream(1, 2, 3, 4).runHead)(equalTo(Some(1)))
      ),
      testM("empty stream")(
        assertM(Stream.empty.runHead)(equalTo(None))
      )
    ),
    suite("Stream interleaving")(
      testM("interleave") {
        val s1 = Stream(2, 3)
        val s2 = Stream(5, 6, 7)

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
      }
    ),
    testM("Stream.iterate")(
      assertM(Stream.iterate(1)(_ + 1).take(10).runCollect)(equalTo((1 to 10).toList))
    ),
    suite("Stream.runLast")(
      testM("nonempty stream")(
        assertM(Stream(1, 2, 3, 4).runLast)(equalTo(Some(4)))
      ),
      testM("empty stream")(
        assertM(Stream.empty.runLast)(equalTo(None))
      )
    ),
    testM("Stream.map")(checkM(pureStreamOfBytes, Gen.function(Gen.anyInt)) { (s, f) =>
      for {
        res1 <- s.map(f).runCollect
        res2 <- s.runCollect.map(_.map(f))
      } yield assert(res1)(equalTo(res2))
    }),
    testM("Stream.mapAccum") {
      assertM(Stream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el)).runCollect)(equalTo(List(1, 2, 3)))
    },
    suite("Stream.mapAccumM")(
      testM("mapAccumM happy path") {
        assertM(
          Stream(1, 1, 1)
            .mapAccumM[Any, Nothing, Int, Int](0)((acc, el) => IO.succeed((acc + el, acc + el)))
            .runCollect
        )(equalTo(List(1, 2, 3)))
      },
      testM("mapAccumM error") {
        Stream(1, 1, 1)
          .mapAccumM(0)((_, _) => IO.fail("Ouch"))
          .runCollect
          .either
          .map(assert(_)(isLeft(equalTo("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("Stream.mapConcat")(checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
      for {
        res1 <- s.mapConcat(f).runCollect
        res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
      } yield assert(res1)(equalTo(res2))
    }),
    testM("Stream.mapConcatChunk")(checkM(pureStreamOfBytes, Gen.function(smallChunks(Gen.anyInt))) { (s, f) =>
      for {
        res1 <- s.mapConcatChunk(f).runCollect
        res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
      } yield assert(res1)(equalTo(res2))
    }),
    suite("Stream.mapConcatChunkM")(
      testM("mapConcatChunkM happy path") {
        checkM(pureStreamOfBytes, Gen.function(smallChunks(Gen.anyInt))) { (s, f) =>
          for {
            res1 <- s.mapConcatChunkM(b => UIO.succeed(f(b))).runCollect
            res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
          } yield assert(res1)(equalTo(res2))
        }
      },
      testM("mapConcatChunkM error") {
        Stream(1, 2, 3)
          .mapConcatChunkM(_ => IO.fail("Ouch"))
          .runCollect
          .either
          .map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    suite("Stream.mapConcatM")(
      testM("mapConcatM happy path") {
        checkM(pureStreamOfBytes, Gen.function(Gen.listOf(Gen.anyInt))) { (s, f) =>
          for {
            res1 <- s.mapConcatM(b => UIO.succeed(f(b))).runCollect
            res2 <- s.runCollect.map(_.flatMap(v => f(v).toSeq))
          } yield assert(res1)(equalTo(res2))
        }
      },
      testM("mapConcatM error") {
        Stream(1, 2, 3)
          .mapConcatM(_ => IO.fail("Ouch"))
          .runCollect
          .either
          .map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("Stream.mapError") {
      Stream
        .fail("123")
        .mapError(_.toInt)
        .runCollect
        .either
        .map(assert(_)(isLeft(equalTo(123))))
    } @@ zioTag(errors),
    testM("Stream.mapErrorCause") {
      Stream
        .halt(Cause.fail("123"))
        .mapErrorCause(_.map(_.toInt))
        .runCollect
        .either
        .map(assert(_)(isLeft(equalTo(123))))
    } @@ zioTag(errors),
    testM("Stream.mapM") {
      checkM(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(successes(Gen.anyByte))) { (data, f) =>
        val s = Stream.fromIterable(data)

        for {
          l <- s.mapM(f).runCollect
          r <- IO.foreach(data)(f)
        } yield assert(l)(equalTo(r))
      }
    },
    testM("Stream.repeatEffect")(
      assertM(
        Stream
          .repeatEffect(IO.succeed(1))
          .take(2)
          .run(Sink.collectAll[Int])
      )(equalTo(List(1, 1)))
    ),
    suite("Stream.repeatEffectOption")(
      testM("emit elements")(
        assertM(
          Stream
            .repeatEffectOption(IO.succeed(1))
            .take(2)
            .run(Sink.collectAll[Int])
        )(equalTo(List(1, 1)))
      ),
      testM("emit elements until pull fails with None")(
        for {
          ref <- Ref.make(0)
          fa = for {
            newCount <- ref.updateAndGet(_ + 1)
            res      <- if (newCount >= 5) ZIO.fail(None) else ZIO.succeed(newCount)
          } yield res
          res <- Stream
                  .repeatEffectOption(fa)
                  .take(10)
                  .run(Sink.collectAll[Int])
        } yield assert(res)(equalTo(List(1, 2, 3, 4)))
      )
    ),
    testM("Stream.repeatEffectWith")(
      Live.live(for {
        ref <- Ref.make[List[Int]](Nil)
        _ <- ZStream
              .repeatEffectWith(ref.update(1 :: _), Schedule.spaced(10.millis))
              .take(2)
              .run(Sink.drain)
        result <- ref.get
      } yield assert(result)(equalTo(List(1, 1))))
    ),
    suite("Stream.mapMPar")(
      testM("foreachParN equivalence") {
        checkM(Gen.small(Gen.listOfN(_)(Gen.anyByte)), Gen.function(successes(Gen.anyByte))) { (data, f) =>
          val s = Stream.fromIterable(data)

          for {
            l <- s.mapMPar(8)(f).runCollect
            r <- IO.foreachParN(8)(data)(f)
          } yield assert(l)(equalTo(r))
        }
      },
      testM("order when n = 1") {
        for {
          queue  <- Queue.unbounded[Int]
          _      <- Stream.range(0, 9).mapMPar(1)(queue.offer).runDrain
          result <- queue.takeAll
        } yield assert(result)(equalTo(result.sorted))
      },
      testM("interruption propagation") {
        for {
          interrupted <- Ref.make(false)
          latch       <- Promise.make[Nothing, Unit]
          fib <- Stream(())
                  .mapMPar(1)(_ => (latch.succeed(()) *> ZIO.infinity).onInterrupt(interrupted.set(true)))
                  .runDrain
                  .fork
          _      <- latch.await
          _      <- fib.interrupt
          result <- interrupted.get
        } yield assert(result)(isTrue)
      } @@ zioTag(interruption),
      testM("guarantee ordering")(checkM(Gen.int(1, 4096), Gen.listOf(Gen.anyInt)) { (n: Int, m: List[Int]) =>
        for {
          mapM    <- Stream.fromIterable(m).mapM(UIO.succeed(_)).runCollect
          mapMPar <- Stream.fromIterable(m).mapMPar(n)(UIO.succeed(_)).runCollect
        } yield assert(n)(isGreaterThan(0)) implies assert(mapM)(equalTo(mapMPar))
      })
    ) @@ TestAspect.forked,
    suite("Stream merging")(
      testM("merge")(checkM(streamOfInts, streamOfInts) { (s1: Stream[String, Int], s2: Stream[String, Int]) =>
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
      testM("mergeEither") {
        val s1 = Stream(1, 2)
        val s2 = Stream(1, 2)

        for {
          merge <- s1.mergeEither(s2).runCollect
        } yield assert(merge)(hasSameElements(List(Left(1), Left(2), Right(1), Right(2))))
      },
      testM("mergeWith") {
        val s1 = Stream(1, 2)
        val s2 = Stream(1, 2)

        for {
          merge <- s1.mergeWith(s2)(_.toString, _.toString).runCollect
        } yield assert(merge)(hasSameElements(List("1", "2", "1", "2")))
      },
      testM("mergeWith short circuit") {
        val s1 = Stream(1, 2)
        val s2 = Stream(1, 2)

        assertM(
          s1.mergeWith(s2)(_.toString, _.toString)
            .run(Sink.succeed[String, String]("done"))
        )(equalTo("done"))
      },
      testM("mergeWith prioritizes failure") {
        val s1 = Stream.never
        val s2 = Stream.fail("Ouch")

        assertM(s1.mergeWith(s2)(_ => (), _ => ()).runCollect.either)(isLeft(equalTo("Ouch")))
      } @@ zioTag(errors)
    ),
    testM("Stream.orElse") {
      val s1 = Stream(1, 2, 3) ++ Stream.fail("Boom")
      val s2 = Stream(4, 5, 6)
      s1.orElse(s2).runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4, 5, 6))))
    },
    testM("Stream.paginate") {
      val s = (0, List(1, 2, 3))

      ZStream
        .paginate(s) {
          case (x, Nil)      => x -> None
          case (x, x0 :: xs) => x -> Some(x0 -> xs)
        }
        .runCollect
        .map(assert(_)(equalTo(List(0, 1, 2, 3))))
    },
    testM("Stream.paginateM") {
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
    suite("Stream.partitionEither")(
      testM("allows repeated runs without hanging") {
        val stream = ZStream
          .fromIterable[Int](Seq.empty)
          .partitionEither(i => ZIO.succeed(if (i % 2 == 0) Left(i) else Right(i)))
          .map { case (evens, odds) => evens.mergeEither(odds) }
          .use(_.runCollect)
        assertM(ZIO.collectAll(Range(0, 100).toList.map(_ => stream)).map(_ => 0))(equalTo(0))
      },
      testM("values") {
        Stream
          .range(0, 6)
          .partitionEither { i =>
            if (i % 2 == 0) ZIO.succeed(Left(i))
            else ZIO.succeed(Right(i))
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
        (Stream.range(0, 1) ++ Stream.fail("Boom")).partitionEither { i =>
          if (i % 2 == 0) ZIO.succeed(Left(i))
          else ZIO.succeed(Right(i))
        }.use {
          case (s1, s2) =>
            for {
              out1 <- s1.runCollect.either
              out2 <- s2.runCollect.either
            } yield assert(out1)(isLeft(equalTo("Boom"))) && assert(out2)(isLeft(equalTo("Boom")))
        }
      } @@ zioTag(errors),
      testM("backpressure") {
        Stream
          .range(0, 6)
          .partitionEither(
            i =>
              if (i % 2 == 0) ZIO.succeed(Left(i))
              else ZIO.succeed(Right(i)),
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
    testM("Stream.peel") {
      val s = Stream('1', '2', ',', '3', '4')
      val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink
        .collectAllWhile[Char](_ == ',')
      assertM(s.peel(parser).use[Any, Int, (Int, Exit[Nothing, List[Char]])] {
        case (n, rest) => rest.runCollect.run.map(n -> _)
      })(equalTo((12, Success(List('3', '4')))))
    },
    testM("Stream.range") {
      assertM(Stream.range(0, 10).runCollect)(equalTo(Range(0, 10).toList))
    },
    suite("Stream.repeat")(
      testM("repeat")(
        assertM(
          Stream(1)
            .repeat(Schedule.recurs(4))
            .run(Sink.collectAll[Int])
        )(equalTo(List(1, 1, 1, 1, 1)))
      ),
      testM("short circuits")(
        Live.live(for {
          ref <- Ref.make[List[Int]](Nil)
          _ <- Stream
                .fromEffect(ref.update(1 :: _))
                .repeat(Schedule.spaced(10.millis))
                .take(2)
                .run(Sink.drain)
          result <- ref.get
        } yield assert(result)(equalTo(List(1, 1))))
      )
    ),
    suite("Stream.repeatEither")(
      testM("emits schedule output")(
        assertM(
          Stream(1)
            .repeatEither(Schedule.recurs(4))
            .run(Sink.collectAll[Either[Int, Int]])
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
        Live.live(for {
          ref <- Ref.make[List[Int]](Nil)
          _ <- Stream
                .fromEffect(ref.update(1 :: _))
                .repeatEither(Schedule.spaced(10.millis))
                .take(3) // take one schedule output
                .run(Sink.drain)
          result <- ref.get
        } yield assert(result)(equalTo(List(1, 1))))
      }
    ),
    suite("Stream.schedule")(
      testM("scheduleElementsWith")(
        assertM(
          Stream("A", "B", "C")
            .scheduleElementsWith(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))(identity, _.toString)
            .run(Sink.collectAll[String])
        )(equalTo(List("A", "123", "B", "123", "C", "123")))
      ),
      testM("scheduleElementsEither")(
        assertM(
          Stream("A", "B", "C")
            .scheduleElementsEither(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))
            .run(Sink.collectAll[Either[Int, String]])
        )(equalTo(List(Right("A"), Left(123), Right("B"), Left(123), Right("C"), Left(123))))
      ),
      testM("scheduleWith")(
        assertM(
          Stream("A", "B", "C", "A", "B", "C")
            .scheduleWith(Schedule.recurs(2) *> Schedule.fromFunction((_) => "Done"))(_.toLowerCase, identity)
            .run(Sink.collectAll[String])
        )(equalTo(List("a", "b", "c", "Done", "a", "b", "c", "Done")))
      ),
      testM("scheduleEither")(
        assertM(
          Stream("A", "B", "C")
            .scheduleEither(Schedule.recurs(2) *> Schedule.fromFunction((_) => "!"))
            .run(Sink.collectAll[Either[String, String]])
        )(equalTo(List(Right("A"), Right("B"), Right("C"), Left("!"))))
      ),
      testM("repeated && assertspaced")(
        assertM(
          Stream("A", "B", "C")
            .scheduleElements(Schedule.once)
            .run(Sink.collectAll[String])
        )(equalTo(List("A", "A", "B", "B", "C", "C")))
      ),
      testM("short circuits in schedule")(
        assertM(
          Stream("A", "B", "C")
            .scheduleElements(Schedule.once)
            .take(4)
            .run(Sink.collectAll[String])
        )(equalTo(List("A", "A", "B", "B")))
      ),
      testM("short circuits after schedule")(
        assertM(
          Stream("A", "B", "C")
            .scheduleElements(Schedule.once)
            .take(3)
            .run(Sink.collectAll[String])
        )(equalTo(List("A", "A", "B")))
      )
    ),
    suite("Stream.take")(
      testM("take")(checkM(streamOfBytes, Gen.anyInt) { (s: Stream[String, Byte], n: Int) =>
        for {
          takeStreamResult <- s.take(n.toLong).runCollect.run
          takeListResult   <- s.runCollect.map(_.take(n)).run
        } yield assert(takeListResult.succeeded)(isTrue) implies assert(takeStreamResult)(equalTo(takeListResult))
      }),
      testM("take short circuits")(
        for {
          ran    <- Ref.make(false)
          stream = (Stream(1) ++ Stream.fromEffect(ran.set(true)).drain).take(0)
          _      <- stream.run(Sink.drain)
          result <- ran.get
        } yield assert(result)(isFalse)
      ),
      testM("take(0) short circuits")(
        for {
          units <- Stream.never.take(0).run(Sink.collectAll[Unit])
        } yield assert(units)(equalTo(Nil))
      ),
      testM("take(1) short circuits")(
        for {
          ints <- (Stream(1) ++ Stream.never).take(1).run(Sink.collectAll[Int])
        } yield assert(ints)(equalTo(List(1)))
      ),
      testM("takeUntil") {
        checkM(streamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
          for {
            streamTakeUntil <- s.takeUntil(p).runCollect.run
            listTakeUntil   <- s.runCollect.map(StreamUtils.takeUntil(_)(p)).run
          } yield assert(listTakeUntil.succeeded)(isTrue) implies assert(streamTakeUntil)(equalTo(listTakeUntil))
        }
      },
      testM("takeWhile")(checkM(streamOfBytes, Gen.function(Gen.boolean)) { (s, p) =>
        for {
          streamTakeWhile <- s.takeWhile(p).runCollect.run
          listTakeWhile   <- s.runCollect.map(_.takeWhile(p)).run
        } yield assert(listTakeWhile.succeeded)(isTrue) implies assert(streamTakeWhile)(equalTo(listTakeWhile))
      }),
      testM("takeWhile short circuits")(
        assertM(
          (Stream(1) ++ Stream.fail("Ouch"))
            .takeWhile(_ => false)
            .runDrain
            .either
        )(isRight(isUnit))
      )
    ),
    testM("Stream.tap") {
      for {
        ref <- Ref.make(0)
        res <- Stream(1, 1).tap[Any, Nothing](a => ref.update(_ + a)).runCollect
        sum <- ref.get
      } yield assert(res)(equalTo(List(1, 1))) && assert(sum)(equalTo(2))
    },
    suite("Stream.timeout")(
      testM("succeed") {
        assertM(
          Stream
            .succeed(1)
            .timeout(Duration.Infinity)
            .runCollect
        )(equalTo(List(1)))
      },
      testM("should interrupt stream") {
        assertM(
          Stream
            .range(0, 5)
            .tap(_ => ZIO.sleep(Duration.Infinity))
            .timeout(Duration.Zero)
            .runDrain
            .sandbox
            .ignore
            .map(_ => true)
        )(isTrue)
      } @@ zioTag(interruption)
    ),
    suite("Stream.throttleEnforce")(
      testM("free elements") {
        assertM(
          Stream(1, 2, 3, 4)
            .throttleEnforce(0, Duration.Infinity)(_ => 0)
            .runCollect
        )(equalTo(List(1, 2, 3, 4)))
      },
      testM("no bandwidth") {
        assertM(
          Stream(1, 2, 3, 4)
            .throttleEnforce(0, Duration.Infinity)(_ => 1)
            .runCollect
        )(equalTo(Nil))
      }
    ),
    suite("Stream.throttleShape")(testM("free elements") {
      assertM(
        Stream(1, 2, 3, 4)
          .throttleShape(1, Duration.Infinity)(_ => 0)
          .runCollect
      )(equalTo(List(1, 2, 3, 4)))
    }),
    testM("Stream.toQueue")(checkM(smallChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
      val s = Stream.fromChunk(c)
      assertM(s.toQueue(1000).use { (queue: Queue[Take[Nothing, Int]]) =>
        waitForSize(queue, c.length + 1) *> queue.takeAll
      })(equalTo(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End))
    }),
    testM("Stream.toQueueUnbounded")(checkM(smallChunks(Gen.anyInt)) { (c: Chunk[Int]) =>
      val s = Stream.fromChunk(c)
      assertM(s.toQueueUnbounded.use { (queue: Queue[Take[Nothing, Int]]) =>
        waitForSize(queue, c.length + 1) *> queue.takeAll
      })(equalTo(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End))
    }),
    suite("Stream.aggregate")(
      testM("aggregate") {
        val s = Stream('1', '2', ',', '3', '4')
        val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink
          .collectAllWhile[Char](_ == ',')

        assertM(s.aggregate(parser).runCollect)(equalTo(List(12, 34)))
      },
      testM("no remainder") {
        val sink = Sink.fold(100)(_ % 2 == 0)((s, a: Int) => (s + a, Chunk[Int]()))
        assertM(ZStream(1, 2, 3, 4).aggregate(sink).runCollect)(equalTo(List(101, 105, 104)))
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

        assertM(ZStream(1, 2, 3).aggregate(sink).runCollect)(equalTo(List(203, 4)))
      },
      testM("with a sink that always signals more") {
        val sink = Sink.foldLeft(0)((s, a: Int) => s + a)
        assertM(ZStream(1, 2, 3).aggregate(sink).runCollect)(equalTo(List(1 + 2 + 3)))
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
          result   <- stream.aggregateManaged(sink).runCollect
          i        <- resource.get
          _        <- if (i != 2000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
        } yield assert(result)(equalTo(List(List(1, 1), List(2, 2), List(3, 3), List(4, 4))))
      },
      testM("propagate managed error") {
        val fail = "I'm such a failure!"
        val sink = ZManaged.fail(fail)
        assertM(ZStream(1, 2, 3).aggregateManaged(sink).runCollect.either)(isLeft(equalTo(fail)))
      } @@ zioTag(errors)
    ),
    testM("Stream.unfold") {
      assertM(
        Stream
          .unfold(0) { i =>
            if (i < 10) Some((i, i + 1))
            else None
          }
          .runCollect
      )(equalTo((0 to 9).toList))
    },
    testM("Stream.unfoldM") {
      assertM(
        Stream
          .unfoldM(0) { i =>
            if (i < 10) IO.succeed(Some((i, i + 1)))
            else IO.succeed(None)
          }
          .runCollect
      )(equalTo((0 to 9).toList))
    },
    testM("Stream.unNone")(checkM(Gen.small(pureStreamGen(Gen.option(Gen.anyInt), _))) { s =>
      for {
        res1 <- (s.unNone.runCollect)
        res2 <- (s.runCollect.map(_.flatten))
      } yield assert(res1)(equalTo(res2))
    }),
    suite("Stream.unTake")(
      testM("unTake happy path") {
        assertM(
          Stream
            .range(0, 10)
            .toQueue[Nothing, Int](1)
            .use(q => Stream.fromQueue(q).unTake.run(Sink.collectAll[Int]))
        )(equalTo(Range(0, 10).toList))
      },
      testM("unTake with error") {
        val e = new RuntimeException("boom")
        assertM(
          (Stream.range(0, 10) ++ Stream.fail(e))
            .toQueue[Throwable, Int](1)
            .use(q => Stream.fromQueue(q).unTake.run(Sink.collectAll[Int]))
            .run
        )(fails(equalTo(e)))
      } @@ zioTag(errors)
    ),
    suite("Stream.via")(
      testM("happy path") {
        val s = Stream(1, 2, 3)
        s.via(_.map(_.toString)).runCollect.map(assert(_)(equalTo(List("1", "2", "3"))))
      },
      testM("introduce error") {
        val s = Stream(1, 2, 3)
        s.via(_ => Stream.fail("Ouch")).runCollect.either.map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    suite("Stream zipping")(
      testM("zipWith") {
        val s1 = Stream(1, 2, 3)
        val s2 = Stream(1, 2)

        assertM(s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _))).runCollect)(equalTo(List(2, 4)))
      },
      testM("zipWithIndex")(checkM(pureStreamOfBytes) { s =>
        for {
          res1 <- (s.zipWithIndex.runCollect)
          res2 <- (s.runCollect.map(_.zipWithIndex.map(t => (t._1, t._2.toLong))))
        } yield assert(res1)(equalTo(res2))
      }),
      testM("zipWith ignore RHS") {
        val s1 = Stream(1, 2, 3)
        val s2 = Stream(1, 2)
        assertM(s1.zipWith(s2)((a, _) => a).runCollect)(equalTo(List(1, 2, 3)))
      },
      testM("zipWith prioritizes failure") {
        assertM(
          Stream.never
            .zipWith(Stream.fail("Ouch"))((_, _) => None)
            .runCollect
            .either
        )(isLeft(equalTo("Ouch")))
      } @@ zioTag(errors),
      testM("zipWithLatest") {
        import zio.test.environment.TestClock

        for {
          q  <- Queue.unbounded[(Int, Int)]
          s1 = Stream.iterate(0)(_ + 1).fixed(100.millis)
          s2 = Stream.iterate(0)(_ + 1).fixed(70.millis)
          s3 = s1.zipWithLatest(s2)((_, _))
          _  <- s3.foreach(q.offer).fork
          a  <- q.take
          _  <- TestClock.setTime(70.millis)
          b  <- q.take
          _  <- TestClock.setTime(100.millis)
          c  <- q.take
          _  <- TestClock.setTime(140.millis)
          d  <- q.take
          _  <- TestClock.setTime(210.millis)
        } yield assert(List(a, b, c, d))(equalTo(List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2)))
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
      } yield assert(streamResult)(equalTo(inputStreamResult))
    },
    testM("Stream.toIterator, Iterators are lazy")((for {
      counter  <- Ref.make(0).toManaged_ //Increment and get the value
      effect   = counter.updateAndGet(_ + 1)
      iterator <- Stream.repeatEffect(effect).toIterator
      n        = 2000
      out <- ZStream
              .fromIteratorTotal(iterator.map(_.merge))
              .mapConcatM(element => effect.map(newElement => List(element, newElement)))
              .take(n.toLong)
              .runCollect
              .toManaged_
    } yield assert(out)(equalTo((1 to n).toList))).use(ZIO.succeed(_)))
  )
}
