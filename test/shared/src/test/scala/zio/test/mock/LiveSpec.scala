package zio.test.mock

import java.util.concurrent.TimeUnit

import scala.concurrent.{ ExecutionContext, Future }

import zio.clock
import zio.DefaultRuntime
import zio.test.TestUtils.label

object LiveSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(liveCanAccessRealEnvironment, "live can access real environment")
  )

  def liveCanAccessRealEnvironment: Future[Boolean] =
    unsafeRunToFuture {
      val io = for {
        mock <- clock.currentTime(TimeUnit.MILLISECONDS)
        live <- Live.live(clock.currentTime(TimeUnit.MILLISECONDS))
      } yield mock == 0 && live != 0
      io.provideManaged(MockEnvironment.Value)
    }
}
