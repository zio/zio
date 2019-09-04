package zio.test

import scala.concurrent.Future

import zio.{ random, Chunk, DefaultRuntime }
import zio.test.Assertion.{ equalTo, isLessThan }
import zio.test.TestUtils.{ label, succeeded }

object CheckSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(effectualPropertiesCanBeTests, "effectual properties can be tested"),
    label(overloadedCheckMethodsWork, "overloaded check methods work"),
    label(testsCanBeWrittenInPropertyBasedStyle, "tests can be written in property based style")
  )

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
}
