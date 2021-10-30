package zio

import zio.test.{Assert, ZSpec, assertTrue}

object StackTracesSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("StackTracesSpec")(
    suite("captureMultiMethod")(
      testM("captures a deep embedded failure") {
        val deepUnderlyingFailure =
          for {
            _ <- ZIO.succeed(5)
            _ <- ZIO.fail("Oh no!").ensuring(ZIO.dieMessage("deep failure"))
          } yield ()

        val underlyingFailure =
          for {
            _ <- ZIO.succeed(15)
            _ <- deepUnderlyingFailure.ensuring(ZIO.dieMessage("other failure"))
          } yield ()

        val failure =
          for {
            _ <- ZIO.succeed(25)
            _ <- underlyingFailure
          } yield ()

        val run: ZIO[Any, Throwable, Any] = failure.catchAllCause(cause => ZIO(cause.prettyPrint))

        assertStackTraceMessage(
          run,
          includesAll(
            Seq(
              "Fiber failed.",
              "╥",
              "╠─A checked error was not handled.",
              "║ Oh no!",
              "╠─An unchecked error was produced.",
              "║ java.lang.RuntimeException: deep failure",
              "╠─An unchecked error was produced.",
              "║ java.lang.RuntimeException: other failure"
            )
          )
        )
      },
      testM("captures the embedded failure") {
        // Given
        val underlyingFailure =
          for {
            _ <- ZIO.succeed(15)
            _ <- ZIO.fail("Oh no!").ensuring(ZIO.dieMessage("other failure"))
          } yield ()

        val failure =
          for {
            _ <- ZIO.succeed(25)
            _ <- underlyingFailure
          } yield ()

        val run: ZIO[Any, Throwable, Any] = failure.catchAllCause(cause => ZIO(cause.prettyPrint))

        assertStackTraceMessage(
          run,
          includesAll(
            Seq(
              "Fiber failed.",
              "╥",
              "╠─A checked error was not handled.",
              "║ Oh no!",
              "╠─An unchecked error was produced.",
              "║ java.lang.RuntimeException: other failure"
            )
          )
        )
      },
      testM("captures a single failure if no suppressed cause") {
        val underlyingFailure =
          for {
            _ <- ZIO.succeed(15)
            _ <- ZIO.fail("Oh no!")
          } yield ()

        val failure =
          for {
            _ <- ZIO.succeed(25)
            _ <- underlyingFailure
          } yield ()

        val run: ZIO[Any, Throwable, Any] = failure.catchAllCause(cause => ZIO(cause.prettyPrint))

        assertStackTraceMessage(
          run,
          (stack: String) =>
            includesAll(Seq("Fiber failed.\nA checked error was not handled.\nOh no!"))(stack)
              && excludesAll(
                Seq(
                  "An unchecked error was produced",
                  "other failure"
                )
              )(stack)
        )
      }
    )
  )

  private def includesAll(texts: Seq[String]): String => Boolean = stack => texts.map(stack.contains).forall(r => r)

  private def excludesAll(texts: Seq[String]): String => Boolean = stack => texts.map(stack.contains).forall(!_)

  private def assertStackTraceMessage(
    failingEffect: ZIO[Any, Throwable, Any],
    assertion: String => Boolean
  ): ZIO[Any, Throwable, Assert] =
    for {
      stackTrace <- failingEffect
      result = stackTrace match {
                 case stackContent: String => assertion(stackContent)
                 case _                    => false
               }
    } yield assertTrue(result)
}
