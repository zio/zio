package zio.test

import zio.Clock.ClockLive
import zio._
import zio.test.TestAspect._

object DemoSpec extends ZIOSpecDefault {

  def spec = suite("outter suite")(
    // ignored, repeated, retried, tagged, timed
    suite("inner suite 1")(
      durationTest("A", 1.seconds) @@ tag("important"),
      durationTest("B", 2.seconds) @@ timed,
    ),
    suite("inner suite 2")(
      durationTest("C", 1.seconds) @@ ignore,
      durationTest("D", 1.seconds) @@ repeats(3),
      durationTest("E", 2.seconds) @@ retries(1),
    ),
  )

  private def durationTest(name: String, duration: Duration) =
    test(name)(ZIO.sleep(duration).withClock(ClockLive) *> assertCompletes)
}
