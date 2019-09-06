package zio.test

import scala.concurrent.Future

import zio.DefaultRuntime
import zio.test.Assertion.equalTo
import zio.test.TestUtils.{ label, succeeded }
import zio.clock._
import zio.test.Assertion._

object TestSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(assertMWorksCorrectly, "assertM works correctly")
  )

  def assertMWorksCorrectly: Future[Boolean] =
    unsafeRunToFuture {
      val spec = testM("time is non-zero") {
        assertM(nanoTime, equalTo(0L))
      }
      succeeded(spec)
    }
}
