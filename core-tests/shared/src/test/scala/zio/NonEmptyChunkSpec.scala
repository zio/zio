package zio

import zio.random.Random
import zio.test.Assertion._
import zio.test._

object NonEmptyChunkSpec extends ZIOBaseSpec {

  lazy val genChunk: Gen[Random with Sized, Chunk[Int]] = Gen.chunkOf(genInt)

  lazy val genInt: Gen[Random, Int] = Gen.int(-10, 10)

  lazy val genIntFunction: Gen[Random, Any => Int] = Gen.function(genInt)

  lazy val genIntFunction2: Gen[Random, (Any, Any) => Int] = Gen.function2(genInt)

  lazy val genNonEmptyChunk: Gen[Random with Sized, NonEmptyChunk[Int]] = Gen.chunkOf1(genInt)

  lazy val genNonEmptyChunkFunction: Gen[Random with Sized, Any => NonEmptyChunk[Int]] = Gen.function(genNonEmptyChunk)

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
    }
  )
}
