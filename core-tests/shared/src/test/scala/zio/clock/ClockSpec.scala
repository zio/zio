package zio.clock

import zio._
import zio.test._

object ClockSpec extends ZIOBaseSpec {

  def spec = suite("ClockSpec")(
    testM("currentDateTime does not throw a DateTimeException") {
      for {
        _ <- clock.currentDateTime
      } yield assertCompletes
    }
  )
}
