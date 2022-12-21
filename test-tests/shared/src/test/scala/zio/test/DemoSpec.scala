package zio.test

import zio.Clock.ClockLive
import zio._

object DemoSpec extends ZIOSpecDefault {

  def spec = suite("outter suite")(
    suite("inner suite 1")(
      durationTest("A", 1.seconds),
      durationTest("B", 2.seconds),
    ),
    suite("inner suite 2")(
      durationTest("C", 1.seconds),
      durationTest("D", 1.seconds),
      durationTest("E", 2.seconds),
    ),
  )

  private def durationTest(name: String, duration: Duration) =
    test(name)(ZIO.sleep(duration).withClock(ClockLive) *> assertCompletes)
}
