package zio.stream

import ChunkUtils._

import zio.random.Random
import zio.test.Assertion.{ equalTo, isLeft }
import zio.test.TestAspect.exceptScala211
import zio.test._
import zio.{ Chunk, IO, NonEmptyChunk, UIO, ZIO, ZIOBaseSpec }

case class Value(i: Int) extends AnyVal

object ChunkSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("ChunkSpec")(
    testM("apply") {
      check(chunkWithIndex(Gen.unit)) {
        case (chunk, i) =>
          assert(chunk.apply(i))(equalTo(chunk.toSeq.apply(i)))
      }
    },
    testM("length") {
      check(largeChunks(intGen))(chunk => assert(chunk.length)(equalTo(chunk.toSeq.length)))
    },
    testM("equality") {
      check(mediumChunks(intGen), mediumChunks(intGen)) { (c1, c2) =>
        assert(c1.equals(c2))(equalTo(c1.toSeq.equals(c2.toSeq)))
      }
    },
    test("inequality") {
      assert(Chunk(1, 2, 3, 4, 5))(Assertion.not(equalTo(Chunk(1, 2, 3, 4, 5, 6))))
    },
    testM("materialize") {
      check(mediumChunks(intGen))(c => assert(c.materialize.toSeq)(equalTo(c.toSeq)))
    },
    testM("foldLeft") {
      check(intGen, Gen.function2(intGen), smallChunks(intGen)) { (s0, f, c) =>
        assert(c.fold(s0)(f))(equalTo(c.toArray.foldLeft(s0)(f)))
      }
    },
    test("mapAccum") {
      assert(Chunk(1, 1, 1).mapAccum(0)((s, el) => (s + el, s + el)))(equalTo((3, Chunk(1, 2, 3))))
    },
    suite("mapAccumM")(
      testM("mapAccumM happy path") {
        assertM(Chunk(1, 1, 1).mapAccumM(0)((s, el) => UIO.succeed((s + el, s + el))))(equalTo((3, Chunk(1, 2, 3))))
      },
      testM("mapAccumM error") {
        Chunk(1, 1, 1).mapAccumM(0)((_, _) => IO.fail("Ouch")).either.map(assert(_)(isLeft(equalTo("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("map") {
      val fn = Gen.function[Random with Sized, Int, Int](intGen)
      check(smallChunks(intGen), fn)((c, f) => assert(c.map(f).toSeq)(equalTo(c.toSeq.map(f))))
    },
    suite("mapM")(
      testM("mapM happy path")(checkM(mediumChunks(intGen), Gen.function(Gen.boolean)) { (chunk, f) =>
        chunk.mapM(s => UIO.succeed(f(s))).map(assert(_)(equalTo(chunk.map(f))))
      }),
      testM("mapM error") {
        Chunk(1, 2, 3).mapM(_ => IO.fail("Ouch")).either.map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("flatMap") {
      val fn = Gen.function[Random with Sized, Int, Chunk[Int]](smallChunks(intGen))
      check(smallChunks(intGen), fn) { (c, f) =>
        assert(c.flatMap(f).toSeq)(equalTo(c.toSeq.flatMap(f.andThen(_.toSeq))))
      }
    },
    testM("headOption") {
      check(mediumChunks(intGen))(c => assert(c.headOption)(equalTo(c.toSeq.headOption)))
    },
    testM("lastOption") {
      check(mediumChunks(intGen))(c => assert(c.lastOption)(equalTo(c.toSeq.lastOption)))
    },
    testM("indexWhere") {
      val fn = Gen.function[Random with Sized, Int, Boolean](Gen.boolean)
      check(mediumChunks(intGen), fn, intGen) { (chunk, p, from) =>
        assert(chunk.indexWhere(p, from).getOrElse(-1))(equalTo(chunk.toSeq.indexWhere(p, from)))
      }
    } @@ exceptScala211,
    testM("exists") {
      val fn = Gen.function[Random with Sized, Int, Boolean](Gen.boolean)
      check(mediumChunks(intGen), fn)((chunk, p) => assert(chunk.exists(p))(equalTo(chunk.toSeq.exists(p))))
    },
    testM("forall") {
      val fn = Gen.function[Random with Sized, Int, Boolean](Gen.boolean)
      check(mediumChunks(intGen), fn)((chunk, p) => assert(chunk.forall(p))(equalTo(chunk.toSeq.forall(p))))
    },
    testM("find") {
      val fn = Gen.function[Random with Sized, Int, Boolean](Gen.boolean)
      check(mediumChunks(intGen), fn)((chunk, p) => assert(chunk.find(p))(equalTo(chunk.toSeq.find(p))))
    },
    testM("filter") {
      val fn = Gen.function[Random with Sized, Int, Boolean](Gen.boolean)
      check(mediumChunks(intGen), fn)((chunk, p) => assert(chunk.filter(p).toSeq)(equalTo(chunk.toSeq.filter(p))))
    },
    suite("filterM")(
      testM("filterM happy path")(checkM(mediumChunks(intGen), Gen.function(Gen.boolean)) { (chunk, p) =>
        chunk.filterM(s => UIO.succeed(p(s))).map(assert(_)(equalTo(chunk.filter(p))))
      }),
      testM("filterM error") {
        Chunk(1, 2, 3).filterM(_ => IO.fail("Ouch")).either.map(assert(_)(equalTo(Left("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("drop chunk") {
      check(largeChunks(intGen), intGen)((chunk, n) => assert(chunk.drop(n).toSeq)(equalTo(chunk.toSeq.drop(n))))
    },
    testM("take chunk") {
      check(chunkWithIndex(Gen.unit)) {
        case (c, n) =>
          assert(c.take(n).toSeq)(equalTo(c.toSeq.take(n)))
      }
    },
    testM("dropWhile chunk") {
      check(mediumChunks(intGen), toBoolFn[Random, Int]) { (c, p) =>
        assert(c.dropWhile(p).toSeq)(equalTo(c.toSeq.dropWhile(p)))
      }
    },
    testM("takeWhile chunk") {
      check(mediumChunks(intGen), toBoolFn[Random, Int]) { (c, p) =>
        assert(c.takeWhile(p).toSeq)(equalTo(c.toSeq.takeWhile(p)))
      }
    },
    testM("toArray") {
      check(mediumChunks(intGen))(c => assert(c.toArray.toSeq)(equalTo(c.toSeq)))
    },
    test("non-homogeneous element type") {
      trait Animal
      trait Cat extends Animal
      trait Dog extends Animal

      val vector   = Vector(new Cat {}, new Dog {}, new Animal {})
      val actual   = Chunk.fromIterable(vector).map(identity)
      val expected = Chunk.fromArray(vector.toArray)

      assert(actual)(equalTo(expected))
    },
    test("toArray for an empty Chunk of type String") {
      assert(Chunk.empty.toArray[String])(equalTo(Array.empty[String]))
    },
    test("to Array for an empty Chunk using filter") {
      assert(Chunk(1).filter(_ == 2).map(_.toString).toArray[String])(equalTo(Array.empty[String]))
    },
    testM("toArray with elements of type String") {
      check(mediumChunks(intGen))(c => assert(c.toArray.toSeq)(equalTo(c.toSeq)))
    },
    test("toArray for a Chunk of any type") {
      val v: Vector[Any] = Vector("String", 1, Value(2))
      assert(Chunk.fromIterable(v).toArray.toVector)(equalTo(v))
    },
    suite("collect")(
      test("collect empty Chunk") {
        assert(Chunk.empty.collect { case _ => 1 } == Chunk.empty)(Assertion.isTrue)
      },
      testM("collect chunk") {
        val pfGen = Gen.partialFunction[Random with Sized, Int, Int](intGen)
        check(mediumChunks(intGen), pfGen)((c, pf) => assert(c.collect(pf).toSeq)(equalTo(c.toSeq.collect(pf))))
      }
    ),
    suite("collectM")(
      testM("collectM empty Chunk") {
        assertM(Chunk.empty.collectM { case _ => UIO.succeed(1) })(equalTo(Chunk.empty))
      },
      testM("collectM chunk") {
        val pfGen = Gen.partialFunction[Random with Sized, Int, UIO[Int]](Gen.successes(intGen))
        checkM(mediumChunks(intGen), pfGen) { (c, pf) =>
          for {
            result   <- c.collectM(pf).map(_.toList)
            expected <- UIO.collectAll(c.toList.collect(pf))
          } yield assert(result)(equalTo(expected))
        }
      },
      testM("collectM chunk that fails") {
        Chunk(1, 2).collectM { case 2 => IO.fail("Ouch") }.either.map(assert(_)(isLeft(equalTo("Ouch"))))
      } @@ zioTag(errors)
    ),
    suite("collectWhile")(
      test("collectWhile empty Chunk") {
        assert(Chunk.empty.collectWhile { case _ => 1 } == Chunk.empty)(Assertion.isTrue)
      },
      testM("collectWhile chunk") {
        val pfGen = Gen.partialFunction[Random with Sized, Int, Int](intGen)
        check(mediumChunks(intGen), pfGen) { (c, pf) =>
          assert(c.collectWhile(pf).toSeq)(equalTo(c.toSeq.takeWhile(pf.isDefinedAt).map(pf.apply)))
        }
      }
    ),
    suite("collectWhileM")(
      testM("collectWhileM empty Chunk") {
        assertM(Chunk.empty.collectWhileM { case _ => UIO.succeed(1) })(equalTo(Chunk.empty))
      },
      testM("collectWhileM chunk") {
        val pfGen = Gen.partialFunction[Random with Sized, Int, UIO[Int]](Gen.successes(intGen))
        checkM(mediumChunks(intGen), pfGen) { (c, pf) =>
          for {
            result   <- c.collectWhileM(pf).map(_.toList)
            expected <- UIO.collectAll(c.toList.takeWhile(pf.isDefinedAt).map(pf.apply))
          } yield assert(result)(equalTo(expected))
        }
      },
      testM("collectWhileM chunk that fails") {
        Chunk(1, 2).collectWhileM { case _ => IO.fail("Ouch") }.either.map(assert(_)(isLeft(equalTo("Ouch"))))
      } @@ zioTag(errors)
    ),
    testM("foreach") {
      check(mediumChunks(intGen)) { c =>
        var sum = 0
        c.foreach(sum += _)

        assert(sum)(equalTo(c.toSeq.sum))
      }
    },
    testM("concat chunk") {
      check(smallChunks(intGen), smallChunks(intGen)) { (c1, c2) =>
        assert((c1 ++ c2).toSeq)(equalTo(c1.toSeq ++ c2.toSeq))
      }
    },
    test("chunk transitivity") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      val c3 = Chunk(1, 2, 3)
      assert(c1 == c2 && c2 == c3 && c1 == c3)(Assertion.isTrue)
    },
    test("chunk symmetry") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 == c2 && c2 == c1)(Assertion.isTrue)
    },
    test("chunk reflexivity") {
      val c1 = Chunk(1, 2, 3)
      assert(c1 == c1)(Assertion.isTrue)
    },
    test("chunk negation") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 != c2 == !(c1 == c2))(Assertion.isTrue)
    },
    test("chunk substitutivity") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 == c2 && c1.toString == c2.toString)(Assertion.isTrue)
    },
    test("chunk consistency") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 == c2 && c1.hashCode == c2.hashCode)(Assertion.isTrue)
    },
    test("nullArrayBug") {
      val c = Chunk.fromArray(Array(1, 2, 3, 4, 5))

      // foreach should not throw
      c.foreach(_ => ())

      assert(c.filter(_ => false).map(_ * 2).length)(equalTo(0))
    },
    test("toArrayOnConcatOfSlice") {
      val onlyOdd: Int => Boolean = _ % 2 != 0
      val concat = Chunk(1, 1, 1).filter(onlyOdd) ++
        Chunk(2, 2, 2).filter(onlyOdd) ++
        Chunk(3, 3, 3).filter(onlyOdd)

      val array = concat.toArray

      assert(array)(equalTo(Array(1, 1, 1, 3, 3, 3)))
    },
    test("toArrayOnConcatOfEmptyAndInts") {
      assert(Chunk.empty ++ Chunk.fromArray(Array(1, 2, 3)))(equalTo(Chunk(1, 2, 3)))
    },
    test("filterConstFalseResultsInEmptyChunk") {
      assert(Chunk.fromArray(Array(1, 2, 3)).filter(_ => false))(equalTo(Chunk.empty))
    },
    test("zipAllWith") {
      assert(Chunk(1, 2, 3).zipAllWith(Chunk(3, 2, 1))(_ => 0, _ => 0)(_ + _))(equalTo(Chunk(4, 4, 4))) &&
      assert(Chunk(1, 2, 3).zipAllWith(Chunk(3, 2))(_ => 0, _ => 0)(_ + _))(equalTo(Chunk(4, 4, 0))) &&
      assert(Chunk(1, 2).zipAllWith(Chunk(3, 2, 1))(_ => 0, _ => 0)(_ + _))(equalTo(Chunk(4, 4, 0)))
    },
    testM("flatMap Non Empty") {
      val fn = Gen.function[Random with Sized, Int, Chunk[Int]](smallChunks(intGen))
      check(smallChunks(intGen), fn) { (c_, f_) =>
        val c: NonEmptyChunk[Int]        = c_ + 0
        val f: Int => NonEmptyChunk[Int] = f_.andThen(_ + 0)

        val in: NonEmptyChunk[Int] = c.flatMap(f)
        val expected: Seq[Int]     = c.toSeq.flatMap(f.andThen(_.toSeq))

        assert(in.toSeq)(equalTo(expected))
      }
    },
    test("nonEmptyChunk subtype preservation") {
      //checks at compile time

      def nonEmptyChunk: NonEmptyChunk[Int] = ???
      def chunk: Chunk[Int]                 = ???
      def x: Int                            = ???

      def checkIsSubtypeOf[A](value: A*): Unit = ???

      checkIsSubtypeOf[NonEmptyChunk[_]](
        Chunk(nonEmptyChunk).flatten,
        Chunk(x),
        Chunk.concat(chunk, nonEmptyChunk),
        Chunk.concat(nonEmptyChunk, chunk),
        chunk + x,
        Chunk.single(x),
        Chunk.succeed(x),
        nonEmptyChunk ++ chunk,
        chunk ++ nonEmptyChunk,
        chunk appendNonEmpty nonEmptyChunk,
        nonEmptyChunk.flatMap(i => Chunk(i)),
        nonEmptyChunk.map(identity),
        nonEmptyChunk.zipAllWith(Chunk(0))(l => (l, l), r => (r, r))((l, r) => (l, r)),
        nonEmptyChunk.zipWithIndex,
        nonEmptyChunk.zipWithIndexFrom(0)
      )

      checkIsSubtypeOf[ZIO[_, _, NonEmptyChunk[_]]](
        nonEmptyChunk.mapM(x => ZIO.succeed(x)),
        nonEmptyChunk.mapMPar(x => ZIO.succeed(x)),
        nonEmptyChunk.mapAccumM("")((s, i) => ZIO.succeed(s + i -> i)).map(_._2)
      )

      assertCompletes
      //checks at compile time
    } @@ TestAspect.ignore
  )
}
