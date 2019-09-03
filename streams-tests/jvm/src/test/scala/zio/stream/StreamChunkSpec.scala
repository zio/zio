package zio.stream

import scala.{ Stream => _ }
import zio.{ Chunk, IO }
import zio.test._
import generators._

object StreamChunkSpec
    extends DefaultRunnableSpec(
      suite("StreamChunkSpec")(
        Spec.test(
          "StreamChunk.map",
          check(streamChunkGen(Gen.string(Gen.anyChar)), Gen[String => Int]) { (s, f) =>
            assertMM(slurp(s.map(f)), slurp(s).map(_.map(f)))
          }
        ),
        Spec.test(
          "StreamChunk.filter",
          check(streamChunkGen(Gen.string(Gen.anyChar)), Gen[String => Boolean]) { (s, p) =>
            assertMM(slurp(s.filter(p)), slurp(s).map(_.filter(p)))
          }
        ),
        Spec.test(
          "StreamChunk.filterNot",
          check(streamChunkGen(Gen.string(Gen.anyChar)), Gen[String => Boolean]) { (s, p) =>
            assertMM(slurp(s.filterNot(p)), slurp(s).map(_.filterNot(p)))
          }
        ),
        Spec.test(
          "StreamChunk.mapConcat",
          check(streamChunkGen(Gen.string(Gen.anyChar)), Gen[String => Chunk[Int]]) { (s, f) =>
            assertMM(slurp(s.mapConcat(f)), slurp(s).map(_.flatMap(v => f(v).toSeq)))
          }
        ),
        Spec.test(
          "StreamChunk.dropWhile",
          check(streamChunkGen(Gen.string(Gen.anyChar)), Gen[String => Boolean]) { (s, p) =>
            assertMM(slurp(s.dropWhile(p)), slurp(s).map(_.dropWhile(p)))
          }
        ),
        Spec.test(
          "StreamChunk.takeWhile",
          check(succeededStreamChunkGen(Gen.string(Gen.anyChar)), Gen[String => Boolean]) { (s, p) =>
            assertMM(slurp(s.takeWhile(p)), slurp(s).map(_.takeWhile(p)))
          }
        ),
        Spec.test(
          "StreamChunk.mapAccum",
          check(streamChunkGen(Gen.anyInt)) { s =>
            val slurped = slurp(s.mapAccum(0)((acc, el) => (acc + el, acc + el)))
            assertMM(slurped, slurp(s).map(_.scanLeft(0)((acc, el) => acc + el).drop(1)))
          }
        ),
        Spec.test("StreamChunk.mapM", check(streamChunkGen(Gen.anyInt), Gen[Int => Int]) { (s, f) =>
          assertMM(slurp(s.mapM(a => IO.succeed(f(a)))), slurp(s).map(_.map(f)))
        }),
        Spec.test(
          "StreamChunk.++",
          check(streamChunkGen(Gen.string(Gen.anyChar)), streamChunkGen(Gen.string(Gen.anyChar))) { (s1, s2) =>
            val listConcat = for {
              left  <- slurp(s1)
              right <- slurp(s2)
            } yield left ++ right
            val streamConcat = slurp(s1 ++ s2)

            assertMM(streamConcat, listConcat)
          }
        ),
        Spec.test("StreamChunk.zipWithIndex", check(streamChunkGen(Gen.string(Gen.anyChar))) { s =>
          assertMM(slurp(s.zipWithIndex), slurp(s).map(_.zipWithIndex))
        }),
        Spec.test(
          "StreamChunk.foreach0",
          check(streamChunkGen(Gen.anyInt), Gen[Int => Boolean]) { (s, cont) =>
            var acc = List[Int]()

            val result = s.foreachWhile { a =>
              IO.effectTotal {
                if (cont(a)) {
                  acc ::= a
                  true
                } else false
              }
            }

            assertMM(result.map(_ => acc.reverse), slurp(s.takeWhile(cont)).map(_.toList))
          }
        ),
        Spec.test(
          "StreamChunk.foreach",
          check(streamChunkGen(Gen.anyInt)) { s =>
            var acc = List[Int]()

            val result = s.foreach(a => IO.effectTotal(acc ::= a))

            assertMM(result.map(_ => acc.reverse), slurp(s).map(_.toList))
          }
        ),
        Spec.test(
          "StreamChunk.monadLaw1",
          check(Gen.anyInt, Gen[Int => StreamChunk[String, Int]]) { (x, f) =>
            assertMM(slurp(ZStreamChunk.succeed(Chunk(x)).flatMap(f)), slurp(f(x)))
          }
        ),
        Spec.test("StreamChunk.monadLaw2", check(streamChunkGen(Gen.anyInt)) { m =>
          assertMM(slurp(m.flatMap(i => ZStreamChunk.succeed(Chunk(i)))), slurp(m))
        }),
        Spec.test(
          "StreamChunk.monadLaw3",
          check(streamChunkGen(Gen.anyInt), Gen[Int => StreamChunk[String, Int]], Gen[Int => StreamChunk[String, Int]]) {
            (m, f, g) =>
              val leftStream  = m.flatMap(f).flatMap(g)
              val rightStream = m.flatMap(x => f(x).flatMap(g))
              assertMM(slurp(leftStream), slurp(rightStream))
          }
        ),
        Spec.test(
          "StreamChunk.tap",
          check(streamChunkGen(Gen.string(Gen.anyChar))) { s =>
            val withoutEffect = slurp(s)
            var acc           = List[String]()
            val tap           = slurp(s.tap(a => IO.effectTotal(acc ::= a)))

            assertMM(tap, withoutEffect) &&
              assertMM(withoutEffect.succeeded Assertion.implies (Success(acc.reverse) must_== withoutEffect) when withoutEffect.succeeded)
          }
        ),
        Spec.test(
          "StreamChunk.foldLeft",
          check(streamChunkGen(Gen.string(Gen.anyChar)), Gen.anyInt, Gen[(Int, String) => Int]) { (s, zero, f) =>
            s.foldLeft(zero)(f) must_=== slurp(s).map(_.foldLeft(zero)(f))
          }
        ),
        Spec.test(
          "StreamChunk.fold",
          check(
            succeededStreamChunkGen(Gen.string(Gen.anyChar)),
            Gen.anyInt,
            Gen[Int => Boolean],
            Gen[(Int, String) => Int]
          ) { (s, zero, cont, f) =>
            val streamResult = s.fold[Any, Nothing, String, Int](zero)(cont)((acc, a) => IO.succeed(f(acc, a)))
            val listResult   = slurp(s).map(l => foldLazyList(l.toList, zero)(cont)(f))
            streamResult must_=== listResult
          }
        ),
        Spec.test(
          "StreamChunk.flattenChunks",
          check(streamChunkGen(Gen.string(Gen.anyChar))) { s =>
            val result = s.flattenChunks.foldLeft[String, List[String]](Nil)((acc, a) => a :: acc).map(_.reverse)
            result must_== slurp(s)
          }
        ),
        Spec.test(
          "StreamChunk.collect",
          check(streamChunkGen(Gen.string(Gen.anyChar)), Gen[PartialFunction[String, String]]) { (s, p) =>
            slurp(s.collect(p)) must_=== slurp(s).map(_.collect(p))
          }
        )
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
