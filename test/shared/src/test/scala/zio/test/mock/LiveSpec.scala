package zio.test.mock

import java.util.concurrent.TimeUnit

import scala.concurrent.{ ExecutionContext, Future }

import zio.{ clock, console, DefaultRuntime }
import zio.duration._
import zio.test.TestUtils.label

object LiveSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(liveCanAccessRealEnvironment, "live can access real environment"),
    label(withLiveProvidesRealEnvironmentToSingleEffect, "withLive provides real environment to single effect")
  )

  def liveCanAccessRealEnvironment: Future[Boolean] =
    unsafeRunToFuture {
      val io = for {
        mock <- clock.currentTime(TimeUnit.MILLISECONDS)
        live <- Live.live(clock.currentTime(TimeUnit.MILLISECONDS))
      } yield mock == 0 && live != 0
      io.provideManaged(MockEnvironment.Value)
    }

  def withLiveProvidesRealEnvironmentToSingleEffect: Future[Boolean] =
    unsafeRunToFuture {
      val io = for {
        _      <- Live.withLive(console.putStr("woot"))(_.delay(1.nanosecond))
        result <- MockConsole.output
      } yield result == Vector("woot")
      io.provideManaged(MockEnvironment.Value)
    }
}
