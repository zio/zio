package zio.test.environment

import java.util.concurrent.TimeUnit

import scala.concurrent.Future

import zio.{ clock, console }
import zio.duration._
import zio.test.Async
import zio.test.TestUtils.label
import zio.test.ZIOBaseSpec

object LiveSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(liveCanAccessRealEnvironment, "live can access real environment"),
    label(withLiveProvidesRealEnvironmentToSingleEffect, "withLive provides real environment to single effect")
  )

  def liveCanAccessRealEnvironment: Future[Boolean] =
    unsafeRunToFuture {
      val io = for {
        test <- clock.currentTime(TimeUnit.MILLISECONDS)
        live <- Live.live(clock.currentTime(TimeUnit.MILLISECONDS))
      } yield test == 0 && live != 0
      io.provideManaged(TestEnvironment.Value)
    }

  def withLiveProvidesRealEnvironmentToSingleEffect: Future[Boolean] =
    unsafeRunToFuture {
      val io = for {
        _      <- Live.withLive(console.putStr("woot"))(_.delay(1.nanosecond))
        result <- TestConsole.output
      } yield result == Vector("woot")
      io.provideManaged(TestEnvironment.Value)
    }
}
