package zio

import zio.test.{ZSpec, assertTrue}

object StackTracesSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("StackTracesSpec")(
    suite("captureSimpleCause")(
      test("captures a simple failure") {
        for {
          _          <- ZIO.succeed(25)
          value       = ZIO.fail("Oh no!")
          stackTrace <- matchPrettyPrintCause(value)
        } yield {
          assertTrue(stackTrace.startsWith("Exception in thread")) &&
          assertTrue(includesAll(Seq("zio-fiber", "java.lang.String: Oh no!"))(stackTrace))
          assertTrue(excludesAll(Seq("Suppressed:"))(stackTrace))
        }
      }
    ),
    suite("captureMultiMethod")(
      test("captures a deep embedded failure") {
        val deepUnderlyingFailure =
          for {
            _ <- ZIO.succeed(5)
            f <- ZIO.fail("Oh no!").ensuring(ZIO.dieMessage("deep failure"))
          } yield f

        val underlyingFailure =
          for {
            _ <- ZIO.succeed(15)
            f <- deepUnderlyingFailure.ensuring(ZIO.dieMessage("other failure"))
          } yield f

        for {
          _     <- ZIO.succeed(25)
          value  = underlyingFailure
          trace <- matchPrettyPrintCause(value)
        } yield {
          show(trace)

          assertTrue(trace.startsWith("Exception in thread")) &&
          assertTrue(numberOfOccurrences("Suppressed")(trace) == 2) &&
          assertTrue(
            includesAll(
              Seq(
                "zio-fiber",
                "java.lang.String: Oh no!",
                "Suppressed: java.lang.RuntimeException: deep failure",
                "Suppressed: java.lang.RuntimeException: other failure"
              )
            )(trace)
          )
        }
      },
      test("captures a deep embedded failure without suppressing the underlying cause") {
        val deepUnderlyingFailure =
          for {
            _ <- ZIO.succeed(5)
            f <- ZIO.fail("Oh no!").ensuring(ZIO.dieMessage("deep failure"))
          } yield f

        val underlyingFailure =
          for {
            _ <- ZIO.succeed(15)
            f <- deepUnderlyingFailure
          } yield f

        for {
          _     <- ZIO.succeed(25)
          value  = underlyingFailure
          trace <- matchPrettyPrintCause(value)
        } yield {
          show(trace)

          assertTrue(trace.startsWith("Exception in thread")) &&
          assertTrue(numberOfOccurrences("Suppressed")(trace) == 1) &&
          assertTrue(
            includesAll(
              Seq(
                "zio-fiber",
                "java.lang.String: Oh no!",
                "Suppressed: java.lang.RuntimeException: deep failure"
              )
            )(trace)
          )
        }
      },
      test("captures the embedded failure") {
        val underlyingFailure =
          for {
            _ <- ZIO.succeed(15)
            f <- ZIO.fail("Oh no!").ensuring(ZIO.dieMessage("other failure"))
          } yield f

        for {
          _     <- ZIO.succeed(25)
          value  = underlyingFailure
          trace <- matchPrettyPrintCause(value)
        } yield {
          show(trace)

          assertTrue(trace.startsWith("Exception in thread")) &&
          assertTrue(numberOfOccurrences("Suppressed")(trace) == 1) &&
          assertTrue(
            includesAll(
              Seq("zio-fiber", "java.lang.String: Oh no!", "Suppressed: java.lang.RuntimeException: other failure")
            )(trace)
          )
        }
      }
    )
  )

  // set to true to print traces
  private val debug = false

  private def show(trace: String): Unit = if (debug) println(trace)

  private def includesAll(texts: Seq[String]): String => Boolean = stack => texts.map(stack.contains).forall(r => r)

  private def numberOfOccurrences(text: String): String => Int = stack =>
    (stack.length - stack.replace(text, "").length) / text.length

  private def excludesAll(texts: Seq[String]): String => Boolean = stack => texts.map(stack.contains).forall(!_)

  private val UnsupportedTestPath: Task[String] = ZIO("not considered scenario")

  private val matchPrettyPrintCause: ZIO[Any, String, Nothing] => ZIO[Any, Throwable, String] = {
    case fail: IO[String, Nothing] =>
      fail.catchAllCause {
        case c: Cause[String] => ZIO(c.prettyPrint)
        case _                => UnsupportedTestPath
      }
    case _ => UnsupportedTestPath
  }
}
