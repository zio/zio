package zio

import zio.ZIOAspect.disableLogging
import zio.test._
import zio.test.TestAspect._

import scala.annotation.tailrec

object LoggingSpec extends ZIOBaseSpec {
  final case class LogEntry(
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan],
    location: ZTraceElement
  ) {
    def call[A](zlogger: ZLogger[String, A]): A =
      zlogger(trace, fiberId, logLevel, message, context, spans, location)
  }

  val _logOutput = new java.util.concurrent.atomic.AtomicReference[Vector[LogEntry]](Vector.empty)

  val logOutput: UIO[Vector[LogEntry]] = UIO(_logOutput.get)

  val clearOutput: UIO[Unit] = UIO(_logOutput.set(Vector.empty))

  val stringLogger: ZLogger[String, Unit] =
    new ZLogger[String, Unit] {
      @tailrec
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan],
        location: ZTraceElement
      ): Unit = if (logLevel >= LogLevel.Info) {
        val newEntry = LogEntry(trace, fiberId, logLevel, message, context, spans, location)

        val oldState = _logOutput.get

        if (!_logOutput.compareAndSet(oldState, oldState :+ newEntry))
          apply(trace, fiberId, logLevel, message, context, spans, location)
        else ()
      }
    }

  val causeLogger: ZLogger[Cause[Any], Unit] = stringLogger.contramap((cause: Cause[Any]) => cause.prettyPrint)

  val testLoggers: ZLogger.Set[String & Cause[Any], Unit] =
    ZLogger.Set(stringLogger, causeLogger)

//  override def runner: TestRunner[Environment, Any] = super.runner.withRuntimeConfig(_.copy(loggers = testLoggers))

  def spec: ZSpec[Any, Any] =
    suite("LoggingSpec")(
      test("simple log message") {
        for {
          _      <- ZIO.log("It's alive!")
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Info)
      },
      test("change log level in region") {
        for {
          _      <- LogLevel.Warning(ZIO.log("It's alive!"))
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Warning)
      },
      test("log at a different log level") {
        for {
          _      <- ZIO.logWarning("It's alive!")
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Warning)
      },
      test("log at a different log level") {
        for {
          _      <- ZIO.logWarning("It's alive!")
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Warning)
      },
      test("log at a span") {
        for {
          _      <- ZIO.logSpan("initial segment")(ZIO.log("It's alive!"))
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).spans(0).label == "initial segment")
      },
      test("default formatter") {
        for {
          _      <- ZIO.logSpan("test span")(ZIO.log("It's alive!"))
          output <- logOutput
          _      <- ZIO.debug(output(0).call(ZLogger.defaultString))
        } yield assertTrue(true)
      },
      test("none") {
        for {
          _      <- ZIO.log("It's alive!") @@ disableLogging
          output <- logOutput
        } yield assertTrue(output.length == 0)
      }
    ) @@ sequential @@ after(clearOutput) @@ TestAspect.runtimeConfig(
      RuntimeConfigAspect.addLogger(stringLogger)
    ) @@ TestAspect.runtimeConfig(RuntimeConfigAspect.addLogger(causeLogger))
}
