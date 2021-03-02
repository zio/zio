package zio

import zio.random.Random
import zio.test.Assertion._
import zio.test._

object NonEmptyChunkSpec extends ZIOBaseSpec {

  lazy val genChunk: Gen[Has[Random] with Sized, Chunk[Int]] = Gen.chunkOf(genInt)

  lazy val genInt: Gen[Has[Random], Int] = Gen.int(-10, 10)

  lazy val genIntFunction: Gen[Has[Random], Any => Int] = Gen.function(genInt)

  lazy val genIntFunction2: Gen[Has[Random], (Any, Any) => Int] = Gen.function2(genInt)

  lazy val genNonEmptyChunk: Gen[Has[Random] with Sized, NonEmptyChunk[Int]] = Gen.chunkOf1(genInt)

  lazy val genNonEmptyChunkFunction: Gen[Has[Random] with Sized, Any => NonEmptyChunk[Int]] =
    Gen.function(genNonEmptyChunk)

  def spec: ZSpec[Environment, Failure] = suite("NonEmptyChunkSpec")(
    testM("+") {
      check(genNonEmptyChunk, genInt)((as, a) => assert((as :+ a).toChunk)(equalTo(as.toChunk :+ a)))
    },
    suite("++")(
      testM("Chunk with NonEmptyChunk") {
        check(genChunk, genNonEmptyChunk)((as, bs) => assert((as ++ bs).toChunk)(equalTo(as ++ bs.toChunk)))
      },
      testM("NonEmptyChunk with Chunk") {
        check(genNonEmptyChunk, genChunk)((as, bs) => assert((as ++ bs).toChunk)(equalTo(as.toChunk ++ bs)))
      },
      testM("NonEmptyChunk with NonEmptyChunk") {
        check(genNonEmptyChunk, genNonEmptyChunk) { (as, bs) =>
          assert((as ++ bs).toChunk)(equalTo(as.toChunk ++ bs.toChunk))
        }
      }
    ),
    testM("flatMap") {
      check(genNonEmptyChunk, genNonEmptyChunkFunction) { (as, f) =>
        assert(as.flatMap(f).toChunk)(equalTo(as.toChunk.flatMap(a => f(a).toChunk)))
      }
    },
    testM("map") {
      check(genNonEmptyChunk, genIntFunction)((as, f) => assert(as.map(f).toChunk)(equalTo(as.toChunk.map(f))))
    },
    testM("reduceMapLeft") {
      check(genNonEmptyChunk, genIntFunction, genIntFunction2) { (as, map, reduce) =>
        val actual   = as.reduceMapLeft(map)(reduce)
        val expected = as.tail.foldLeft(map(as.head))(reduce)
        assert(actual)(equalTo(expected))
      }
    },
    testM("reduceMapRight") {
      check(genNonEmptyChunk, genIntFunction, genIntFunction2) { (as, map, reduce) =>
        val actual   = as.reduceMapRight(map)(reduce)
        val expected = as.init.foldRight(map(as.last))(reduce)
        assert(actual)(equalTo(expected))
      }
    },
    suite("unapplySeq")(
      test("matches a nonempty chunk") {
        val chunk = Chunk(1, 2, 3)
        val actual = chunk match {
          case NonEmptyChunk(x, y, z) => Some((x, y, z))
          case _                      => None
        }
        val expected = Some((1, 2, 3))
        assert(actual)(equalTo(expected))
      },
      test("does not match an empty chunk") {
        val chunk = Chunk.empty
        val actual = chunk match {
          case NonEmptyChunk(x, y, z) => Some((x, y, z))
          case _                      => None
        }
        val expected = None
        assert(actual)(equalTo(expected))
      },
      test("does not match another collection type") {
        val vector = Vector(1, 2, 3)
        val actual = vector match {
          case NonEmptyChunk(x, y, z) => Some((x, y, z))
          case _                      => None
        }
        val expected = None
        assert(actual)(equalTo(expected))
      }
    )
  )
}
