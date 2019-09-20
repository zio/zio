package zio

import zio.test._
import zio.test.Assertion._

object ZIOSpec
    extends ZIOBaseSpec(
      suite("ZIO#parallelErrors")(
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
      )
    )
