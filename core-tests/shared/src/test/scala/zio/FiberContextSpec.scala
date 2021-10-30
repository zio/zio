package zio

import zio.test.ZSpec
import zio.test._
import zio.test.Assertion._

object FiberContextSpec extends ZIOBaseSpec {
  override def spec: ZSpec[Environment, Failure] =
    suite("FiberContextSpec")(
      testM("Get fiber and print fiber context info")(
        for {
          fiber <- ZIO.succeed(()).fork
          info  <- ZIO.succeed({ println(fiber.toString()); fiber.toString() })
          _     <- fiber.join
        } yield assert(info)(containsString("FiberContext") && containsString("fiberId"))
      )
    )

}
