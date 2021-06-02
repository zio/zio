package zio

import zio.test._

import java.time.DateTimeException

object ClockSpec extends ZIOBaseSpec {

  def spec: Spec[Has[Clock], TestFailure[DateTimeException], TestSuccess] = suite("ClockSpec")(
    testM("currentDateTime does not throw a DateTimeException") {
      for {
        _ <- Clock.currentDateTime
      } yield assertCompletes
    }
  )
}
