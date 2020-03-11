package zio.stream.experimental

import ZStreamGen._

import zio._
import zio.stream.ChunkUtils._
import zio.test.Assertion._
import zio.test._

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
      suite("zips")(
        testM("zip") {
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
        // testM("zipWithIndex")(checkM(pureStreamOfBytes) { s =>
        //   for {
        //     res1 <- (s.zipWithIndex.runCollect)
        //     res2 <- (s.runCollect.map(_.zipWithIndex.map(t => (t._1, t._2.toLong))))
        //   } yield assert(res1)(equalTo(res2))
        // }),
        testM("zipAllLeft") {
          val s1 = ZStream(1, 2, 3)
          val s2 = ZStream(1, 2)
          assertM((s1.zipAllLeft(s2)(0)).runCollect)(equalTo(List(1, 2, 3)))
        },
        testM("zipWith prioritizes failure") {
          assertM(
            ZStream.never
              .zipWith(ZStream.fail("Ouch"))((_, _) => None)
              .runCollect
              .either
          )(isLeft(equalTo("Ouch")))
        }
        // testM("zipWithLatest") {
        //   import zio.test.environment.TestClock

        //   for {
        //     q  <- Queue.unbounded[(Int, Int)]
        //     s1 = Stream.iterate(0)(_ + 1).fixed(100.millis)
        //     s2 = Stream.iterate(0)(_ + 1).fixed(70.millis)
        //     s3 = s1.zipWithLatest(s2)((_, _))
        //     _  <- s3.foreach(q.offer).fork
        //     a  <- q.take
        //     _  <- TestClock.setTime(70.millis)
        //     b  <- q.take
        //     _  <- TestClock.setTime(100.millis)
        //     c  <- q.take
        //     _  <- TestClock.setTime(140.millis)
        //     d  <- q.take
        //     _  <- TestClock.setTime(210.millis)
        //   } yield assert(List(a, b, c, d))(equalTo(List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2)))
        // }
      )
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
