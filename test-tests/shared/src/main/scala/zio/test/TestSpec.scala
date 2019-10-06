package zio.test

import scala.concurrent.Future

import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.TestUtils.{ failed, label, succeeded }
import zio.clock._
import zio.test.Assertion._

object TestSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(assertMWorksCorrectly, "assertM works correctly"),
    label(testMErrorIsTestFailure, "testM error is test failure"),
    label(testMIsPolymorphicInErrorType, "testM is polymorphic in error type")
  )

  def assertMWorksCorrectly: Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("time is non-zero") {
        assertM(nanoTime, equalTo(0L))
      }
      succeeded(spec)
    }

  def testMErrorIsTestFailure: Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("error") {
        for {
          _      <- ZIO.fail("fail")
          result <- ZIO.succeed("succeed")
        } yield assert(result, equalTo("succeed"))
      }
      failed(spec)
    }

  def testMIsPolymorphicInErrorType: Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("success") {
        for {
          _      <- ZIO.effect(())
          result <- ZIO.succeed("succeed")
        } yield assert(result, equalTo("succeed"))
      }
      succeeded(spec)
    }
}
