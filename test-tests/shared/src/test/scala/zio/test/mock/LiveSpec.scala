package zio.test

import zio._
import zio.test.Assertion._

import java.util.concurrent.TimeUnit

object LiveSpec extends ZIOBaseSpec {

  def spec = suite("LiveSpec")(
    test("live can access real environment") {
      for {
        test <- Clock.currentTime(TimeUnit.MILLISECONDS)
        live <- Live.live(Clock.currentTime(TimeUnit.MILLISECONDS))
      } yield assert(test)(equalTo(0L)) && assert(live)(not(equalTo(0L)))
    },
    test("withLive provides real environment to single effect") {
      for {
        _      <- Live.withLive(Console.print("woot"))(_.delay(1.nanosecond))
        result <- TestConsole.output
      } yield assert(result)(equalTo(Vector("woot")))
    }
  )
}
