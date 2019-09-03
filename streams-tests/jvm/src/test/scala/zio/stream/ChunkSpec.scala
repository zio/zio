package zio.stream

import zio.Chunk
import zio.test._
import zio.test.Assertion.equalTo
import StreamTestUtils._

object ChunkSpec
    extends DefaultRunnableSpec(
      suite("ChunkSpec")(
        testM("apply") {
          check(chunkWithLength) {
            case (chunk, i) =>
              assert(chunk.apply(i), equalTo(chunk.toSeq.apply(i)))
          }
        },
        testM("length") {
          check(chunkGen(Gen.anyInt)) { chunk =>
            assert(chunk.length, equalTo(chunk.toSeq.length))
          }
        },
        testM("equality") {
          check(chunkGen(Gen.anyInt), chunkGen(Gen.anyInt)) { (c1, c2) =>
            assert(c1.equals(c2), equalTo(c1.toSeq.equals(c2.toSeq)))
          }
        },
        test("inequality") {
          assert(Chunk(1, 2, 3, 4, 5), Assertion.not(equalTo(Chunk(1, 2, 3, 4, 5, 6))))
        },
        testM("materialize") {
          check(chunkGen(Gen.anyInt)) { c =>
            assert(c.materialize.toSeq, equalTo(c.toSeq))
          }
        },
        testM("foldLeft") {
          check(Gen.string(Gen.anyChar), Gen[(String, Int) => String], chunkGen(Gen.anyInt)) { (s0, f, c) =>
            assert(c.foldLeft(s0)(f), equalTo(c.toArray.foldLeft(s0)(f)))
          }
        },
        testM("map") {
          check(chunkGen(Gen.anyInt), Gen[Int => String]) { (c, f) =>
            assert(c.map(f).toSeq, equalTo(c.toSeq.map(f)))
          }
        },
        testM("flatMap") {
          check(chunkGen(Gen.anyInt), Gen[Int => Chunk[Int]]) { (c, f) =>
            assert(c.flatMap(f).toSeq, equalTo(c.toSeq.flatMap(f.andThen(_.toSeq))))
          }
        },
        testM("filter") {
          check(chunkGen(Gen.string(Gen.anyChar)), Gen[String => Boolean]) { (chunk, p) =>
            assert(chunk.filter(p).toSeq, equalTo(chunk.toSeq.filter(p)))
          }
        },
        testM("drop chunk") {
          check(chunkGen(Gen.anyInt), Gen.anyInt) { (chunk, n) =>
            assert(chunk.drop(n).toSeq, equalTo(chunk.toSeq.drop(n)))
          }
        },
        testM("take chunk") {
          check(chunkWithLength) {
            case (c, n) =>
              assert(c.take(n).toSeq, equalTo(c.toSeq.take(n)))
          }
        },
        testM("dropWhile chunk") {
          check(chunkGen(Gen.anyInt), Gen[Int => Boolean]) { (c, p) =>
            assert(c.dropWhile(p).toSeq, equalTo(c.toSeq.dropWhile(p)))
          }
        },
        testM("takeWhile chunk") {
          check(chunkGen(Gen.anyInt), Gen[Int => Boolean]) { (c, p) =>
            assert(c.takeWhile(p).toSeq, equalTo(c.toSeq.takeWhile(p)))
          }
        },
        testM("toArray") {
          check(chunkGen(Gen.anyInt)) { c =>
            assert(c.toArray.toSeq, equalTo(c.toSeq))
          }
        },
        testM("foreach") {
          check(chunkGen(Gen.anyInt)) { c =>
            var sum = 0
            c.foreach(sum += _)

            assert(sum, equalTo(c.toSeq.sum))
          }
        },
        testM("concat chunk") {
          check(chunkGen(Gen.anyInt), chunkGen(Gen.anyInt)) { (c1, c2) =>
            assert((c1 ++ c2).toSeq, equalTo(c1.toSeq ++ c2.toSeq))
          }
        },
        test("chunk transitivity") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          val c3 = Chunk(1, 2, 3)
          assert(c1 == c2 && c2 == c3 && c1 == c3, Assertion.isTrue)
        },
        test("chunk symmetry") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 == c2 && c2 == c1, Assertion.isTrue)
        },
        test("chunk reflexivity") {
          val c1 = Chunk(1, 2, 3)
          assert(c1 == c1, Assertion.isTrue)
        },
        test("chunk negation") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 != c2 == !(c1 == c2), Assertion.isTrue)
        },
        test("chunk substitutivity") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 == c2 && c1.toString == c2.toString, Assertion.isTrue)
        },
        test("chunk consistency") {
          val c1 = Chunk(1, 2, 3)
          val c2 = Chunk(1, 2, 3)
          assert(c1 == c2 && c1.hashCode == c2.hashCode, Assertion.isTrue)
        },
        test("nullArrayBug") {
          val c = Chunk.fromArray(Array(1, 2, 3, 4, 5))

          // foreach should not throw
          c.foreach(_ => ())

          assert(c.filter(_ => false).map(_ * 2).length, equalTo(0))
        },
        test("toArrayOnConcatOfSlice") {
          val onlyOdd: Int => Boolean = _ % 2 != 0
          val concat = Chunk(1, 1, 1).filter(onlyOdd) ++
            Chunk(2, 2, 2).filter(onlyOdd) ++
            Chunk(3, 3, 3).filter(onlyOdd)

          val array = concat.toArray

          assert(array, equalTo(Array(1, 1, 1, 3, 3, 3)))
        },
        test("toArrayOnConcatOfEmptyAndInts") {
          assert(Chunk.empty ++ Chunk.fromArray(Array(1, 2, 3)), equalTo(Array(1, 2, 3)))
        },
        test("filterConstFalseResultsInEmptyChunk") {
          assert(Chunk.fromArray(Array(1, 2, 3)).filter(_ => false), equalTo(Chunk.empty))
        }
      )
    )
