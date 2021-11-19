package zio

import zio.test._

import java.time.DateTimeException

object ClockSpec extends ZIOBaseNewSpec {

  def spec: Spec[Clock, TestFailure[DateTimeException], TestSuccess] = suite("ClockSpec")(
    test("currentDateTime does not throw a DateTimeException") {
      for {
        _ <- Clock.currentDateTime
      } yield assertCompletes
    }
  )
}
