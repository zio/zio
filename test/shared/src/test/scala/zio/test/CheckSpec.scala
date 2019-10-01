package zio.test

import scala.concurrent.Future

import zio.{ random, Chunk, Ref, ZIO }
import zio.test.Assertion._
import zio.test.TestUtils.{ execute, failed, forAllTests, label, succeeded }

object CheckSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(checkMIsPolymorphicInErrorType, "checkM is polymorphic in error type"),
    label(effectualPropertiesCanBeTests, "effectual properties can be tested"),
    label(errorInCheckMIsTestFailure, "error in checkM is test failure"),
    label(overloadedCheckMethodsWork, "overloaded check methods work"),
    label(maxShrinksIsRespected, "max shrinks is respected"),
    label(testsCanBeWrittenInPropertyBasedStyle, "tests can be written in property based style"),
    label(testsWithFilteredGeneratorsTerminate, "tests with filtered generators terminate"),
    label(failingTestsContainGenFailureDetails, "failing tests contain gen failure details")
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

  def implicationWorksCorrectly: Future[Boolean] =
    unsafeRunToFuture {
      val sortedProp = testM("sorted") {
        check(Gen.listOf(Gen.int(-10, 10))) { ns =>
          val nss      = ns.sorted
          val nonEmpty = assert(nss, hasSize(isGreaterThan(0)))
          val sorted   = assert(nss.zip(nss.tail).exists { case (a, b) => a > b }, isFalse)
          nonEmpty ==> sorted
        }
      }
      succeeded(sortedProp)
    }

  def maxShrinksIsRespected: Future[Boolean] = {
    val gen = Gen.listOfN(10)(Gen.int(-10, 10))
    unsafeRunToFuture {
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
      } yield result <= 1200
    }
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

  def failingTestsContainGenFailureDetails: Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("GenFailureDetails") {
        check(Gen.anyInt) { a =>
          assert(a, isGreaterThan(0))
        }
      }
      forAllTests(execute(spec)) {
        case Left(TestFailure.Assertion(BoolAlgebra.Value(details))) => details.gen.fold(false)(_.shrinkedInput == 0)
        case _                                                       => false
      }
    }
}
