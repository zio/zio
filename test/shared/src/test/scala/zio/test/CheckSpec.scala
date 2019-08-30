package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.{ random, Chunk, DefaultRuntime }
import zio.test.Assertion.{ equalTo, isLessThan }
import zio.test.TestUtils.label

object CheckSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(effectualPropertiesCanBeTests, "effectual properties can be tested"),
    label(overloadedCheckMethodsWork, "overloaded check methods work"),
    label(testsCanBeWrittenInPropertyBasedStyle, "tests can be written in property based style")
  )

  def effectualPropertiesCanBeTests: Future[Boolean] =
    unsafeRunToFuture {
      val nextInt = checkM(Gen.int(1, 100)) { n =>
        for {
          r <- random.nextInt(n)
        } yield assert(r, isLessThan(n))
      }
      nextInt.map(_.success)
    }

  def overloadedCheckMethodsWork: Future[Boolean] =
    unsafeRunToFuture {
      val associativity = check(Gen.anyInt, Gen.anyInt, Gen.anyInt) { (x, y, z) =>
        assert((x + y) + z, equalTo(x + (y + z)))
      }
      associativity.map(_.success)
    }

  def testsCanBeWrittenInPropertyBasedStyle: Future[Boolean] =
    unsafeRunToFuture {
      val chunkWithLength = for {
        n      <- Gen.int(1, 100)
        i      <- Gen.int(0, n - 1)
        vector <- Gen.vectorOfN(n)(Gen.int(0, 100))
        chunk  = Chunk.fromIterable(vector)
      } yield (chunk, i)
      val chunkApply = check(chunkWithLength) {
        case (chunk, i) =>
          assert(chunk.apply(i), equalTo(chunk.toSeq.apply(i)))
      }
      chunkApply.map(_.success)
    }
}
