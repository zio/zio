package zio.test

import scala.concurrent.Future

import zio.{ random, Chunk, DefaultRuntime, ZIO }
import zio.test.Assertion.{ equalTo, isLessThan }
import zio.test.TestUtils.{ failed, label, succeeded }

object CheckSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(checkMIsPolymorphicInErrorType, "checkM is polymorphic in error type"),
    label(effectualPropertiesCanBeTests, "effectual properties can be tested"),
    label(errorInCheckMIsTestFailure, "error in checkM is test failure"),
    label(overloadedCheckMethodsWork, "overloaded check methods work"),
    label(testsCanBeWrittenInPropertyBasedStyle, "tests can be written in property based style"),
    label(testsWithFilteredGeneratorsTerminate, "tests with filtered generators terminate")
  )

  def checkMIsPolymorphicInErrorType: Future[Boolean] =
    unsafeRunToFuture {
      val nextInt = testM("nextInt") {
        checkM(Gen.int(1, 100)) { n =>
          for {
            _ <- ZIO.effect(())
            r <- random.nextInt(n)
          } yield assert(r, isLessThan(n))
        }
      }
      succeeded(nextInt)
    }

  def effectualPropertiesCanBeTests: Future[Boolean] =
    unsafeRunToFuture {
      val nextInt = testM("nextInt") {
        checkM(Gen.int(1, 100)) { n =>
          for {
            r <- random.nextInt(n)
          } yield assert(r, isLessThan(n))
        }
      }
      succeeded(nextInt)
    }

  def errorInCheckMIsTestFailure: Future[Boolean] =
    unsafeRunToFuture {
      val nextInt = testM("nextInt") {
        checkM(Gen.int(1, 100)) { n =>
          for {
            _ <- ZIO.fail("fail")
            r <- random.nextInt(n)
          } yield assert(r, isLessThan(n))
        }
      }
      failed(nextInt)
    }

  def overloadedCheckMethodsWork: Future[Boolean] =
    unsafeRunToFuture {
      val associativity = testM("associativity") {
        check(Gen.anyInt, Gen.anyInt, Gen.anyInt) { (x, y, z) =>
          assert((x + y) + z, equalTo(x + (y + z)))
        }
      }
      succeeded(associativity)
    }

  def testsCanBeWrittenInPropertyBasedStyle: Future[Boolean] =
    unsafeRunToFuture {
      val chunkWithLength = for {
        n      <- Gen.int(1, 100)
        i      <- Gen.int(0, n - 1)
        vector <- Gen.vectorOfN(n)(Gen.int(0, 100))
        chunk  = Chunk.fromIterable(vector)
      } yield (chunk, i)
      val chunkApply = testM("chunk.apply") {
        check(chunkWithLength) {
          case (chunk, i) =>
            assert(chunk.apply(i), equalTo(chunk.toSeq.apply(i)))
        }
      }
      succeeded(chunkApply)
    }

  def testsWithFilteredGeneratorsTerminate: Future[Boolean] =
    unsafeRunToFuture {
      val filtered = testM("filtered") {
        check(Gen.anyInt.filter(_ > 0), Gen.anyInt.filter(_ > 0)) { (a, b) =>
          assert(a, equalTo(b))
        }
      }
      failed(filtered)
    }
}
