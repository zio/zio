package zio.clock

import zio._
import zio.test.Assertion._
import zio.test._

import java.util.concurrent.TimeUnit

object ClockSpecJVM extends ZIOBaseSpec {

  def spec: Spec[Annotations with TestConfig with ZTestEnv, TestFailure[Any], TestSuccess] = suite("ClockSpec")(
    testM("currentDateTime has microsecond resolution") {
      val unit = TimeUnit.MICROSECONDS
      for {
        a <- clock.currentTime(unit)
        _ <- ZIO.foreach(1 to 1000)(_ => UIO.unit) // just pass some time
        b <- clock.currentTime(unit)
      } yield assert((b - a) % 1000)(not(equalTo(0L)))
    }.provideLayer(Clock.live) @@ TestAspect.flaky
  )
}
