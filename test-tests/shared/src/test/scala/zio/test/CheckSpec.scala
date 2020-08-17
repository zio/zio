package zio.test

import zio.test.Assertion._
import zio.test.TestAspect.failing
import zio.{ random, Chunk, Ref, ZIO }

object CheckSpec extends ZIOBaseSpec {

  def spec = suite("CheckSpec")(
    testM("checkM is polymorphic in error type") {
      checkM(Gen.int(1, 100)) { n =>
        for {
          _ <- ZIO.effect(())
          r <- random.nextIntBounded(n)
        } yield assert(r)(isLessThan(n))
      }
    },
    testM("effectual properties can be tested") {
      checkM(Gen.int(1, 100)) { n =>
        for {
          r <- random.nextIntBounded(n)
        } yield assert(r)(isLessThan(n))
      }
    },
    testM("error in checkM is test failure") {
      checkM(Gen.int(1, 100)) { n =>
        for {
          _ <- ZIO.fail("fail")
          r <- random.nextIntBounded(n)
        } yield assert(r)(isLessThan(n))
      }
    } @@ failing,
    testM("overloaded check methods work") {
      check(Gen.anyInt, Gen.anyInt, Gen.anyInt)((x, y, z) => assert((x + y) + z)(equalTo(x + (y + z))))
    },
    testM("max shrinks is respected") {
      val gen = Gen.listOfN(10)(Gen.int(-10, 10))
      for {
        ref <- Ref.make(0)
        _ <- checkM(gen <*> gen) { _ =>
               for {
                 _ <- ref.update(_ + 1)
                 p <- random.nextIntBounded(10).map(_ != 0)
               } yield assert(p)(isTrue)
             }
        result <- ref.get
      } yield assert(result)(isLessThan(1200))
    },
    testM("tests can be written in property based style") {
      val chunkWithLength = for {
        n      <- Gen.int(1, 100)
        i      <- Gen.int(0, n - 1)
        vector <- Gen.vectorOfN(n)(Gen.int(0, 100))
        chunk   = Chunk.fromIterable(vector)
      } yield (chunk, i)
      check(chunkWithLength) {
        case (chunk, i) =>
          assert(chunk.apply(i))(equalTo(chunk.toList.apply(i)))
      }
    },
    testM("tests with filtered generators terminate") {
      check(Gen.anyInt.filter(_ > 0), Gen.anyInt.filter(_ > 0))((a, b) => assert(a)(equalTo(b)))
    } @@ failing,
    testM("failing tests contain gen failure details") {
      check(Gen.anyInt)(a => assert(a)(isGreaterThan(0))).map {
        _.failures match {
          case Some(BoolAlgebra.Value(details)) => details.gen.fold(false)(_.shrunkenInput == 0)
          case _                                => false
        }
      }.map(assert(_)(isTrue))
    },
    testM("implication works correctly") {
      check(Gen.listOf1(Gen.int(-10, 10))) { ns =>
        val nss      = ns.sorted
        val nonEmpty = assert(nss)(hasSize(isGreaterThan(0)))
        val sorted   = assert(nss.zip(nss.tail).exists { case (a, b) => a > b })(isFalse)
        nonEmpty ==> sorted
      }
    },
    testM("checkM effect type is correctly inferred") {
      checkM(Gen.unit) { _ =>
        for {
          _ <- random.nextInt
        } yield assertCompletes
      }
    }
  )
}
