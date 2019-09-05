package zio.stream

import scala.{ Stream => _ }
import zio.{ Chunk, IO, Ref }
import zio.test._
import zio.test.Assertion.equalTo
import StreamTestUtils._

object StreamChunkSpec
    extends DefaultRunnableSpec(
      suite("StreamChunkSpec")(
        testM("StreamChunk.map") {
          check(streamChunkGen(Gen.anyString), Gen[String => Int]) { (s, f) =>
            for {
              res1 <- slurp(s.map(f))
              res2 <- slurp(s).map(_.map(f))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.filter") {
          check(streamChunkGen(Gen.anyString), Gen[String => Boolean]) { (s, p) =>
            for {
              res1 <- slurp(s.filter(p))
              res2 <- slurp(s).map(_.filter(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.filterNot") {
          check(streamChunkGen(Gen.anyString), Gen[String => Boolean]) { (s, p) =>
            for {
              res1 <- slurp(s.filterNot(p))
              res2 <- slurp(s).map(_.filterNot(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.mapConcat") {
          check(streamChunkGen(Gen.anyString), Gen[String => Chunk[Int]]) { (s, f) =>
            for {
              res1 <- slurp(s.mapConcat(f))
              res2 <- slurp(s).map(_.flatMap(v => f(v).toSeq))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.drop") {
          check(streamChunkGen(Gen.anyString)) { (s, n) =>
            assert(slurp(s.drop(n)), equalTo(slurp(s).map(_.drop(n))))
          }
        },
        test("StreamChunk.take") {
          check(streamChunkGen(Gen.anyString)) { (s, n) =>
            assert(slurp(s.take(n)), equalTo(slurp(s).map(_.take(n))))
          }
        },
            testM("StreamChunk.dropWhile") {
          check(streamChunkGen(Gen.anyString), Gen[String => Boolean]) { (s, p) =>
            for {
              res1 <- slurp(s.dropWhile(p))
              res2 <- slurp(s).map(_.dropWhile(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.takeWhile") {
          check(succeededStreamChunkGen(Gen.anyString), Gen[String => Boolean]) { (s, p) =>
            for {
              res1 <- slurp(s.takeWhile(p))
              res2 <- slurp(s).map(_.takeWhile(p))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.mapAccum") {
          check(streamChunkGen(Gen.anyInt)) { s =>
            for {
              res1 <- slurp(s.mapAccum(0)((acc, el) => (acc + el, acc + el)))
              res2 <- slurp(s).map(_.scanLeft(0)((acc, el) => acc + el).drop(1))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM(
          "StreamChunk.mapM",
          check(streamChunkGen(Gen.anyInt), Gen[Int => Int]) { (s, f) =>
            for {
              res1 <- slurp(s.mapM(a => IO.succeed(f(a))))
              res2 <- slurp(s).map(_.map(f))
            } yield assert(res1, equalTo(res2))
          }
        ),
        testM("StreamChunk.++") {
          check(streamChunkGen(Gen.anyString), streamChunkGen(Gen.anyString)) { (s1, s2) =>
            for {
              res1 <- slurp(s1).zipWith(s2, _ ++ _)
              res2 <- slurp(s1 ++ s2)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM(
          "StreamChunk.zipWithIndex",
          check(streamChunkGen(Gen.anyString)) { s =>
            for {
              res1 <- slurp(s.zipWithIndex)
              res2 <- slurp(s).map(_.zipWithIndex)
            } yield assert(res1, equalTo(res2))
          }
        ),
        testM("StreamChunk.foreach0") {
          check(streamChunkGen(Gen.anyInt), Gen[Int => Boolean]) { (s, cont) =>
            for {
              acc <- Ref.make[List[Int]](Nil)
              res1 <- s.foreachWhile { a =>
                       if (cont(a))
                         acc.update(a :: _) *> ZIO.succeed(true)
                       else
                         ZIO.succeed(false)
                     }.flatMap(_ => acc.update(_.reverse))
              res2 <- slurp(s.takeWhile(cont)).map(_.toList)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.foreach") {
          check(streamChunkGen(Gen.anyInt)) { s =>
            for {
              acc  <- Ref.make[List[Int]](Nil)
              res1 <- s.foreach(a => acc.update(a :: _)).map(_ => acc.reverse)
              res2 <- slurp(s).map(_.toList)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.monadLaw1") {
          check(Gen.anyInt, Gen[Int => StreamChunk[String, Int]]) { (x, f) =>
            for {
              res1 <- slurp(ZStreamChunk.succeed(Chunk(x)).flatMap(f))
              res2 <- slurp(f(x))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.monadLaw2") {
          check(streamChunkGen(Gen.anyInt)) { m =>
            for {
              res1 <- slurp(m.flatMap(i => ZStreamChunk.succeed(Chunk(i))))
              res2 <- slurp(m)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.monadLaw3") {
          check(streamChunkGen(Gen.anyInt), Gen[Int => StreamChunk[String, Int]], Gen[Int => StreamChunk[String, Int]]) {
            (m, f, g) =>
              val leftStream  = m.flatMap(f).flatMap(g)
              val rightStream = m.flatMap(x => f(x).flatMap(g))

              for {
                res1 <- slurp(leftStream)
                res2 <- slurp(rightStream)
              } yield assert(res1, equalTo(res2))
          }
        },
        // testM("StreamChunk.tap") {
        //   check(streamChunkGen(Gen.anyString)) { s =>
        //     val withoutEffect = slurp(s)
        //     var acc           = List[String]()
        //     val tap           = slurp(s.tap(a => IO.effectTotal(acc ::= a)))

        //     assertMM(tap, withoutEffect) &&
        //       assertMM(withoutEffect.succeeded Assertion.implies (Success(acc.reverse) must_== withoutEffect) when withoutEffect.succeeded)
        //   }
        // },
        testM("StreamChunk.foldLeft") {
          check(streamChunkGen(Gen.anyString), Gen.anyInt, Gen[(Int, String) => Int]) { (s, zero, f) =>
            for {
              res1 <- s.foldLeft(zero)(f)
              res2 <- slurp(s).map(_.foldLeft(zero)(f))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.fold") {
          check(
            succeededStreamChunkGen(Gen.anyString),
            Gen.anyInt,
            Gen[Int => Boolean],
            Gen[(Int, String) => Int]
          ) { (s, zero, cont, f) =>
            for {
              res1 <- s.fold[Any, Nothing, String, Int](zero)(cont)((acc, a) => IO.succeed(f(acc, a)))
              res2 <- slurp(s).map(l => foldLazyList(l.toList, zero)(cont)(f))
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.flattenChunks") {
          check(streamChunkGen(Gen.anyString)) { s =>
            for {
              res1 <- s.flattenChunks.foldLeft[String, List[String]](Nil)((acc, a) => a :: acc).map(_.reverse)
              res2 <- slurp(s)
            } yield assert(res1, equalTo(res2))
          }
        },
        testM("StreamChunk.collect") {
          check(streamChunkGen(Gen.anyString), Gen[PartialFunction[String, String]]) { (s, p) =>
            for {
              res1 <- slurp(s.collect(p))
              res2 <- slurp(s).map(_.collect(p))
            } yield assert(res1, equalTo(res2))
          }
        }
      )
    )
//   private def foldLazyList[S, T](list: List[T], zero: S)(cont: S => Boolean)(f: (S, T) => S): S = {
//     @tailrec
//     def loop(xs: List[T], state: S): S = xs match {
//       case head :: tail if cont(state) => loop(tail, f(state, head))
//       case _                           => state
//     }
//     loop(list, zero)
//   }
