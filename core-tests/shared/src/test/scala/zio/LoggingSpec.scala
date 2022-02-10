package zio

import zio.ZIOAspect.disableLogging
import zio.test._

object LoggingSpec extends ZIOBaseSpec {

  def spec: ZSpec[Any, Any] =
    suite("LoggingSpec")(
      test("simple log message") {
        for {
          _      <- ZIO.log("It's alive!")
          output <- ZTestLogger.logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Info)
      },
      test("change log level in region") {
        for {
          _      <- LogLevel.Warning(ZIO.log("It's alive!"))
          output <- ZTestLogger.logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Warning)
      },
      test("log at a different log level") {
        for {
          _      <- ZIO.logWarning("It's alive!")
          output <- ZTestLogger.logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Warning)
      },
      test("log at a different log level") {
        for {
          _      <- ZIO.logWarning("It's alive!")
          output <- ZTestLogger.logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Warning)
      },
      test("log at a span") {
        for {
          _      <- ZIO.logSpan("initial segment")(ZIO.log("It's alive!"))
          output <- ZTestLogger.logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).spans(0).label == "initial segment")
      },
      test("default formatter") {
        for {
          _      <- ZIO.logSpan("test span")(ZIO.log("It's alive!"))
          output <- ZTestLogger.logOutput
          _      <- ZIO.debug(output(0).call(ZLogger.defaultString))
        } yield assertTrue(true)
      },
      test("none") {
        for {
          _      <- ZIO.log("It's alive!") @@ disableLogging
          output <- ZTestLogger.logOutput
        } yield assertTrue(output.length == 0)
      },
      test("log annotations") {
        val key   = "key"
        val value = "value"
        for {
          _      <- ZIO.logAnnotate(key, value)(ZIO.log("It's alive!"))
          output <- ZTestLogger.logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).annotations(key) == value)
      }
    )
}
