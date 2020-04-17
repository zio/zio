package zio.stream

import scala.{ Stream => _ }

import com.github.ghik.silencer.silent

import zio._
import zio.random.Random
import zio.stream.StreamChunkUtils._
import zio.test.Assertion.{ equalTo, isFalse, isLeft, succeeds }
import zio.test._

object StreamChunkSpec extends ZIOBaseSpec {

  import ZIOTag._

  def tinyChunks[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] =
    Gen.chunkOfBounded(0, 3)(a)

  def smallChunks[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] =
    Gen.small(Gen.chunkOfN(_)(a))

  val intGen       = Gen.int(-10, 10)
  val chunksOfInts = pureStreamChunkGen(smallChunks(intGen))

  def toBoolFn[R <: Random, A]: Gen[R, A => Boolean] =
    Gen.function(Gen.boolean)

  def spec = suite("StreamChunkSpec")(
    testM("StreamChunk.catchAllCauseErrors") {
      val s1 = StreamChunk(Stream(Chunk(1), Chunk(2, 3))) ++ StreamChunk(Stream.fail("Boom"))
      val s2 = StreamChunk(Stream(Chunk(4, 5), Chunk(6)))
      s1.catchAllCause(_ => s2).flattenChunks.runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4, 5, 6))))
    } @@ zioTag(errors),
    testM("StreamChunk.catchAllCauseDefects") {
      val s1 = StreamChunk(Stream(Chunk(1), Chunk(2, 3))) ++ StreamChunk(Stream.dieMessage("Boom"))
      val s2 = StreamChunk(Stream(Chunk(4, 5), Chunk(6)))
      s1.catchAllCause(_ => s2).flattenChunks.runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4, 5, 6))))
    } @@ zioTag(errors),
    testM("StreamChunk.catchAllCauseHappyPath") {
      val s1 = StreamChunk(Stream(Chunk(1), Chunk(2, 3)))
      val s2 = StreamChunk(Stream(Chunk(4, 5), Chunk(6)))
      s1.catchAllCause(_ => s2).flattenChunks.runCollect.map(assert(_)(equalTo(List(1, 2, 3))))
    },
    testM("StreamChunk.catchAllCauseFinalizers") {
      for {
        fins   <- Ref.make(List[String]())
        s1     = StreamChunk((Stream(Chunk(1), Chunk(2, 3)) ++ Stream.fail("Boom")).ensuring(fins.update("s1" :: _)))
        s2     = StreamChunk((Stream(Chunk(4, 5), Chunk(6)) ++ Stream.fail("Boom")).ensuring(fins.update("s2" :: _)))
        _      <- s1.catchAllCause(_ => s2).flattenChunks.runCollect.run
        result <- fins.get
      } yield assert(result)(equalTo(List("s2", "s1")))
    },
    testM("StreamChunk.either") {
      val s = StreamChunk(Stream(Chunk(1), Chunk(2, 3))) ++ StreamChunk(Stream.fail("Boom"))
      s.either.flattenChunks.runCollect.map(assert(_)(equalTo(List(Right(1), Right(2), Right(3), Left("Boom")))))
    },
    testM("StreamChunk.orElse") {
      val s1 = StreamChunk(Stream(Chunk(1), Chunk(2, 3))) ++ StreamChunk(Stream.fail("Boom"))
      val s2 = StreamChunk(Stream(Chunk(4, 5), Chunk(6)))
      s1.orElse(s2).flattenChunks.runCollect.map(assert(_)(equalTo(List(1, 2, 3, 4, 5, 6))))
    },
    testM("StreamChunk.map") {
      checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, f) =>
        for {
          res1 <- slurp(s.map(f))
          res2 <- slurp(s).map(_.map(f))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.filter") {
      checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, p) =>
        for {
          res1 <- slurp(s.filter(p))
          res2 <- slurp(s).map(_.filter(p))
        } yield assert(res1)(equalTo(res2))
      }
    },
    suite("StreamChunk.filterM")(
      testM("filterM happy path")(checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, p) =>
        for {
          res1 <- slurp(s.filterM(s => UIO.succeed(p(s))))
          res2 <- slurp(s).map(_.filter(p))
        } yield assert(res1)(equalTo(res2))
      }),
      testM("filterM error") {
        Chunk(1, 2, 3).filterM(_ => IO.fail("Ouch")).either.map(assert(_)(equalTo(Left("Ouch"))))
      }
    ),
    testM("StreamChunk.filterNot") {
      checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, p) =>
        for {
          res1 <- slurp(s.filterNot(p))
          res2 <- slurp(s).map(_.filterNot(p))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.mapConcat") {
      val fn = Gen.function[Random with Sized, Int, Iterable[Int]](Gen.small(Gen.listOfN(_)(intGen)))
      checkM(pureStreamChunkGen(tinyChunks(intGen)), fn) { (s, f) =>
        for {
          res1 <- slurp(s.mapConcat(f))
          res2 <- slurp(s).map(_.flatMap(v => f(v)))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.mapConcatChunk") {
      val fn = Gen.function[Random with Sized, Int, Chunk[Int]](smallChunks(intGen))
      checkM(pureStreamChunkGen(tinyChunks(intGen)), fn) { (s, f) =>
        for {
          res1 <- slurp(s.mapConcatChunk(f))
          res2 <- slurp(s).map(_.flatMap(v => f(v)))
        } yield assert(res1)(equalTo(res2))
      }
    },
    suite("StreamChunk.mapConcatChunkM")(
      testM("mapConcatChunkM happy path") {
        val fn = Gen.function[Random with Sized, Int, Chunk[Int]](smallChunks(intGen))
        checkM(pureStreamChunkGen(tinyChunks(intGen)), fn) { (s, f) =>
          for {
            res1 <- slurp(s.mapConcatChunkM(s => UIO.succeed(f(s))))
            res2 <- slurp(s).map(_.flatMap(s => f(s)))
          } yield assert(res1)(equalTo(res2))
        }
      },
      testM("mapConcatM error") {
        StreamChunk
          .succeed(Chunk.single(1))
          .mapConcatChunkM(_ => IO.fail("Ouch"))
          .run(Sink.drain)
          .either
          .map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    suite("StreamChunk.mapConcatM")(
      testM("mapConcatM happy path") {
        val fn = Gen.function[Random with Sized, Int, Iterable[Int]](Gen.listOf(intGen))
        checkM(pureStreamChunkGen(tinyChunks(intGen)), fn) { (s, f) =>
          for {
            res1 <- slurp(s.mapConcatM(s => UIO.succeed(f(s))))
            res2 <- slurp(s).map(_.flatMap(s => f(s)))
          } yield assert(res1)(equalTo(res2))
        }
      },
      testM("mapConcatM error") {
        StreamChunk
          .succeed(Chunk.single(1))
          .mapConcatM(_ => IO.fail("Ouch"))
          .run(Sink.drain)
          .either
          .map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("StreamChunk.mapError") {
      StreamChunk(Stream.fail("123"))
        .mapError(_.toInt)
        .run(Sink.drain)
        .either
        .map(assert(_)(isLeft(equalTo(123))))
    } @@ zioTag(errors),
    testM("StreamChunk.mapErrorCause") {
      StreamChunk(Stream.halt(Cause.fail("123")))
        .mapErrorCause(_.map(_.toInt))
        .run(Sink.drain)
        .either
        .map(assert(_)(isLeft(equalTo(123))))
    } @@ zioTag(errors),
    testM("StreamChunk.drop") {
      checkM(chunksOfInts, intGen) { (s, n) =>
        for {
          res1 <- slurp(s.drop(n))
          res2 <- slurp(s).map(_.drop(n))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.take") {
      checkM(chunksOfInts, intGen) { (s, n) =>
        for {
          res1 <- slurp(s.take(n))
          res2 <- slurp(s).map(_.take(n))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.dropUntil") {
      checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, p) =>
        for {
          res1 <- slurp(s.dropUntil(p))
          res2 <- slurp(s).map(seq => StreamUtils.dropUntil(seq.toList)(p))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.dropWhile") {
      checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, p) =>
        for {
          res1 <- slurp(s.dropWhile(p))
          res2 <- slurp(s).map(_.dropWhile(p))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.takeUntil") {
      checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, p) =>
        for {
          res1 <- slurp(s.takeUntil(p))
          res2 <- slurp(s).map(seq => StreamUtils.takeUntil(seq.toList)(p))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.takeWhile") {
      checkM(chunksOfInts, toBoolFn[Random with Sized, Int]) { (s, p) =>
        for {
          res1 <- slurp(s.takeWhile(p))
          res2 <- slurp(s).map(_.takeWhile(p))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.mapAccum") {
      checkM(chunksOfInts) { s =>
        for {
          res1 <- slurp(s.mapAccum(0)((acc, el) => (acc + el, acc + el)))
          res2 <- slurp(s).map(_.scanLeft(0)((acc, el) => acc + el).drop(1))
        } yield assert(res1)(equalTo(res2))
      }
    },
    suite("StreamChunk.mapAccumM")(
      testM("mapAccumM happy path") {
        checkM(chunksOfInts) { s =>
          for {
            res1 <- slurp(s.mapAccumM(0)((acc, el) => UIO.succeed((acc + el, acc + el))))
            res2 <- slurp(s).map(_.scanLeft(0)((acc, el) => acc + el).drop(1))
          } yield assert(res1)(equalTo(res2))
        }
      },
      testM("mapAccumM error") {
        StreamChunk
          .fromChunks(Chunk(1), Chunk(2, 3), Chunk.empty)
          .mapAccumM(0)((_, _) => IO.fail("Ouch"))
          .run(Sink.drain)
          .either
          .map(assert(_)(isLeft(equalTo("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("StreamChunk.mapM") {
      checkM(chunksOfInts, Gen.function[Random, Int, Int](intGen)) { (s, f) =>
        for {
          res1 <- slurp(s.mapM(a => IO.succeed(f(a))))
          res2 <- slurp(s).map(_.map(f))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.++") {
      checkM(chunksOfInts, chunksOfInts) { (s1, s2) =>
        for {
          res1 <- slurp(s1).zipWith(slurp(s2))(_ ++ _)
          res2 <- slurp(s1 ++ s2)
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.zipWithIndex") {
      checkM(chunksOfInts) { s =>
        for {
          res1 <- slurp(s.zipWithIndex)
          res2 <- slurp(s).map(_.zipWithIndex.map(t => (t._1, t._2.toLong)))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.foreach0") {
      checkM(chunksOfInts, toBoolFn[Random, Int]) { (s, cont) =>
        for {
          acc <- Ref.make[List[Int]](Nil)
          res1 <- s.foreachWhile { a =>
                   if (cont(a))
                     acc.update(a :: _) *> IO.succeed(true)
                   else
                     IO.succeed(false)
                 }.flatMap(_ => acc.updateAndGet(_.reverse))
          res2 <- slurp(s.takeWhile(cont)).map(_.toList)
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.foreach") {
      checkM(chunksOfInts) { s =>
        for {
          acc  <- Ref.make[List[Int]](Nil)
          res1 <- s.foreach(a => acc.update(a :: _).unit).flatMap(_ => acc.updateAndGet(_.reverse))
          res2 <- slurp(s).map(_.toList)
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.monadLaw1") {
      val fn = Gen.function[Random with Sized, Int, StreamChunk[Nothing, Int]](chunksOfInts)
      checkM(intGen, fn) { (x, f) =>
        for {
          res1 <- slurp(ZStreamChunk.succeed(Chunk(x)).flatMap(f))
          res2 <- slurp(f(x))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.monadLaw2") {
      checkM(chunksOfInts) { m =>
        for {
          res1 <- slurp(m.flatMap(i => ZStreamChunk.succeed(Chunk(i))))
          res2 <- slurp(m)
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.monadLaw3") {
      val otherInts1 = pureStreamChunkGen(tinyChunks(Gen.int(0, 100)))
      val otherInts2 = pureStreamChunkGen(tinyChunks(Gen.int(-100, -1)))
      val fn1        = Gen.function[Random with Sized, Int, StreamChunk[Nothing, Int]](otherInts1)
      val fn2        = Gen.function[Random with Sized, Int, StreamChunk[Nothing, Int]](otherInts2)
      checkNM(5)(pureStreamChunkGen(tinyChunks(intGen)), fn1, fn2) { (m, f, g) =>
        val leftStream: StreamChunk[Nothing, Int]  = m.flatMap(f).flatMap(g)
        val rightStream: StreamChunk[Nothing, Int] = m.flatMap(f(_).flatMap(g))

        for {
          res1 <- slurp(leftStream)
          res2 <- slurp(rightStream)
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.tap") {
      checkM(chunksOfInts) { s =>
        for {
          acc           <- Ref.make(List.empty[Int])
          withoutEffect <- slurp(s).run
          tap           <- slurp(s.tap(a => acc.update(a :: _).unit)).run
          list          <- acc.get.run
        } yield {
          assert(withoutEffect)(equalTo(tap)) && (assert(withoutEffect.succeeded)(isFalse) || assert(withoutEffect)(
            equalTo(list.map(_.reverse))
          ))
        }
      }
    },
    suite("StreamChunk.via")(
      testM("happy path") {
        val s = StreamChunk.fromChunks(Chunk(1), Chunk.empty, Chunk(2, 3, 4), Chunk(5, 6))
        s.via(_.map(_.toString)).runCollect.map(assert(_)(equalTo(List("1", "2", "3", "4", "5", "6"))))
      },
      testM("introduce error") {
        val s = StreamChunk.fromChunks(Chunk(1), Chunk.empty, Chunk(2, 3, 4), Chunk(5, 6))
        s.via(_ => StreamChunk(Stream.fail("Ouch"))).runCollect.either.map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("StreamChunk.fold") {
      checkM(chunksOfInts, intGen, Gen.function2(intGen)) { (s, zero, f) =>
        for {
          res1 <- s.fold(zero)(f)
          res2 <- slurp(s).map(_.foldLeft(zero)(f))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.foldWhileM") {
      checkM(
        chunksOfInts,
        intGen,
        toBoolFn[Random, Int],
        Gen.function2(intGen)
      ) { (s, zero, cont, f) =>
        for {
          res1 <- s.foldWhileM(zero)(cont)((acc, a) => IO.succeed(f(acc, a)))
          res2 <- slurp(s).map(l => foldLazyList(l.toList, zero)(cont)(f))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.flattenChunks") {
      checkM(chunksOfInts) { s =>
        for {
          res1 <- s.flattenChunks.fold[List[Int]](Nil)((acc, a) => a :: acc).map(_.reverse)
          res2 <- slurp(s)
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.collect") {
      checkM(
        pureStreamChunkGen(smallChunks(intGen)),
        Gen.partialFunction[Random with Sized, Int, String](Gen.anyString)
      ) { (s, p) =>
        for {
          res1 <- slurp(s.collect(p))
          res2 <- slurp(s).map(_.collect(p))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.collectWhile") {
      checkM(
        pureStreamChunkGen(smallChunks(intGen)),
        Gen.partialFunction[Random with Sized, Int, Int](intGen)
      ) { (s, pf) =>
        for {
          res1 <- slurp(s.collectWhile(pf))
          res2 <- slurp(s).map(_.takeWhile(pf.isDefinedAt).map(pf.apply))
        } yield assert(res1)(equalTo(res2))
      }
    },
    testM("StreamChunk.toInputStream") {
      val orig1  = List(1, 2, 3).map(_.toByte)
      val orig2  = List(4).map(_.toByte)
      val stream = StreamChunk.fromChunks(Chunk.fromIterable(orig1), Chunk[Byte](), Chunk.fromIterable(orig2))
      @silent("Any")
      val inputStreamResult = stream.toInputStream.use { inputStream =>
        ZIO.succeed(
          Iterator
            .continually(inputStream.read)
            .takeWhile(_ != -1)
            .map(_.toByte)
            .toList
        )
      }
      assertM(inputStreamResult.run)(succeeds(equalTo(orig1 ++ orig2)))
    },
    testM("StreamChunk.ensuring") {
      for {
        log <- Ref.make(List.empty[String])
        _ <- (for {
              _ <- StreamChunk(
                    Stream
                      .bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                      .flatMap(_ => Stream.succeed(Chunk(())))
                  )
              _ <- StreamChunk(Stream.fromEffect(log.update("Use" :: _)).flatMap(_ => Stream.empty))
            } yield ()).ensuring(log.update("Ensuring" :: _)).run(Sink.drain)
        execution <- log.get
      } yield assert(execution)(equalTo(List("Ensuring", "Release", "Use", "Acquire")))
    },
    testM("StreamChunk.ensuringFirst") {
      for {
        log <- Ref.make(List.empty[String])
        _ <- (for {
              _ <- StreamChunk(
                    Stream
                      .bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
                      .flatMap(_ => Stream.succeed(Chunk(())))
                  )
              _ <- StreamChunk(Stream.fromEffect(log.update("Use" :: _)).flatMap(_ => Stream.empty))
            } yield ()).ensuringFirst(log.update("Ensuring" :: _)).run(Sink.drain)
        execution <- log.get
      } yield assert(execution)(equalTo(List("Release", "Ensuring", "Use", "Acquire")))
    },
    testM("StreamChunk.ChunkN") {
      val s1 = StreamChunk(Stream(Chunk(1, 2, 3, 4, 5), Chunk(6, 7), Chunk(8, 9, 10, 11)))
      assertM(s1.chunkN(2).chunks.map(_.toList).runCollect)(
        equalTo(List(List(1, 2), List(3, 4), List(5, 6), List(7, 8), List(9, 10), List(11)))
      )
    },
    testM("StreamChunk.ChunkN Non-Empty") {
      val s1 = StreamChunk(Stream(Chunk(1), Chunk(2), Chunk(3)))
      assertM(s1.chunkN(1).chunks.map(_.toList).runCollect)(equalTo(List(List(1), List(2), List(3))))
    }
  )
}
