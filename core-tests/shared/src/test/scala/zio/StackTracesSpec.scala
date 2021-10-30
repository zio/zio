package zio

import zio.test.{ZSpec, assertTrue}

object StackTracesSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("StackTracesSpec")(
    suite("captureSimpleCause")(
      test("captures a simple failure") {
        val failure =
          for {
            _ <- ZIO.succeed(15)
            _ <- ZIO.fail("Oh no!")
          } yield ()

        val run = failure.catchAllCause(cause => ZIO(cause.prettyPrint))

        for {
          stackTrace <- run
          result = stackTrace match {
                     case stack: String =>
                       stack.startsWith("Exception in thread") &&
                         stack.contains("zio-fiber") &&
                         stack.contains("java.lang.String: Oh no!")
                     case _ => false
                   }
        } yield assertTrue(result)
      }
    )
  )
}
