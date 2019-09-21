package zio

import zio.duration._
import zio.test._
import zio.test.mock.live
import zio.test.Assertion._

object ZIOSpec
    extends ZIOBaseSpec(
      suite("ZIO")(
        suite("parallelErrors")(
          testM("oneFailure") {
            for {
              f1     <- IO.fail("error1").fork
              f2     <- IO.succeed("success1").fork
              errors <- f1.zip(f2).join.parallelErrors[String].flip
            } yield assert(errors, equalTo(List("error1")))
          },
          testM("allFailures") {
            for {
              f1     <- IO.fail("error1").fork
              f2     <- IO.fail("error2").fork
              errors <- f1.zip(f2).join.parallelErrors[String].flip
            } yield assert(errors, equalTo(List("error1", "error2")))
          }
        ),
        suite("raceAll")(
          testM("returns first sucess") {
            assertM(ZIO.fail("Fail").raceAll(List(IO.succeed(24))), equalTo(24))
          },
          testM("returns last failure") {
            assertM(live(ZIO.sleep(100.millis) *> ZIO.fail(24)).raceAll(List(ZIO.fail(25))).flip, equalTo(24))
          },
          testM("returns success when it happens after failure") {
            assertM(ZIO.fail(42).raceAll(List(IO.succeed(24) <* live(ZIO.sleep(100.millis)))), equalTo(24))
          }
        )
      )
    )
