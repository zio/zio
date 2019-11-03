package zio.test

import zio.{Chunk, Ref, ZIO, random}
import zio.test.TestUtils.execute
import zio.test.Assertion._

object CheckSpec extends ZIOBaseSpec(
  suite("CheckSpec")(
    suite("test")(
    testM("checkM is polymorphic in error type") {
      checkM(Gen.int(1, 100)) { n =>
        for {
          _ <- ZIO.effect(())
          r <- random.nextInt(n)
        } yield assert(r, isLessThan(n))
      }
    },
    testM("effectual properties can be tested") {
      checkM(Gen.int(1, 100)) { n =>
        for {
          r <- random.nextInt(n)
        } yield assert(r, isLessThan(n))
      }
    },
    testM("error in checkM is test failure") {
      checkM(Gen.int(1, 100)) { n =>
        for {
          _ <- ZIO.fail("fail")
          r <- random.nextInt(n)
        } yield assert(r, isLessThan(n))
      }
    },
    testM("overloaded check methods work") {
      check(Gen.anyInt, Gen.anyInt, Gen.anyInt) { (x, y, z) =>
        assert((x + y) + z, equalTo(x + (y + z)))
      }
    },
    testM("max shrinks is respected") {
      val gen = Gen.listOfN(10)(Gen.int(-10, 10))
      for {
        ref <- Ref.make(0)
        _ <- execute {
          testM("shrink") {
            checkM(gen <*> gen) { _ =>
              for {
                _ <- ref.update(_ + 1)
                p <- random.nextInt(10).map(_ != 0)
              } yield assert(p, isTrue)
            }
          }
        }
        result <- ref.get
      } yield assert(result, isLessThan(1200))
    },
    testM("tests can be written in property based style") {
      val chunkWithLength = for {
        n <- Gen.int(1, 100)
        i <- Gen.int(0, n - 1)
        vector <- Gen.vectorOfN(n)(Gen.int(0, 100))
        chunk = Chunk.fromIterable(vector)
      } yield (chunk, i)
      check(chunkWithLength) {
        case (chunk, i) =>
          assert(chunk.apply(i), equalTo(chunk.toSeq.apply(i)))
      }
    },
    testM("tests with filtered generators terminate") {
      check(Gen.anyInt.filter(_ > 0), Gen.anyInt.filter(_ > 0)) { (a, b) =>
        assert(a, equalTo(b))
      }
    },
    /**testM("failing tests contain gen failure details") {
      /** val spec1 = testM("GenFailureDetails") {
        * check(Gen.anyInt) { a =>
        * assert(a, isGreaterThan(0))
        * }
        * } */

      //check(Gen.anyInt) { a =>
      for {
        ref <- Ref.make(0)
        spec = testM("GenFailureDetails") {
          check(Gen.anyInt) { a => assert(a, isGreaterThan(0)) }
        }
        res <- execute(spec)
      } yield assert(res.isInstanceOf[TestFailure.Assertion], isTrue)
      //}
    },*/
    testM("implicationWorksCorrectly") {
      check(Gen.listOf(Gen.int(-10, 10))) { ns =>
        val nss = ns.sorted
        val nonEmpty = assert(nss, hasSize(isGreaterThan(0)))
        val sorted = assert(nss.zip(nss.tail).exists { case (a, b) => a > b }, isFalse)
        nonEmpty ==> sorted
      }
    }
  )
)
)
