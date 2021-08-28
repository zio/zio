package zio

import zio.test._
import zio.test.TestAspect._

import zio.internal.stacktracer.ZTraceElement
import zio.internal.ZLogger
import scala.annotation.tailrec

object LoggingSpec extends ZIOBaseSpec {
  final case class LogEntry(
    trace: ZTraceElement,
    fiberId: Fiber.Id,
    logLevel: LogLevel,
    message: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan]
  ) {
    def call[A](zlogger: ZLogger[A]): A =
      zlogger(trace, fiberId, logLevel, message, context, spans)
  }

  val _logOutput = new java.util.concurrent.atomic.AtomicReference[Vector[LogEntry]](Vector.empty)

  val logOutput: UIO[Vector[LogEntry]] = UIO(_logOutput.get)

  val clearOutput: UIO[Unit] = UIO(_logOutput.set(Vector.empty))

  val testLogger: ZLogger[Unit] =
    new ZLogger[Unit] {
      @tailrec
      def apply(
        trace: ZTraceElement,
        fiberId: Fiber.Id,
        logLevel: LogLevel,
        message: () => String,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): Unit = {
        val newEntry = LogEntry(trace, fiberId, logLevel, message, context, spans)

        val oldState = _logOutput.get

        if (!_logOutput.compareAndSet(oldState, oldState :+ newEntry))
          apply(trace, fiberId, logLevel, message, context, spans)
        else ()
      }
    }

  override def runner: TestRunner[Environment, Any] = super.runner.withPlatform(_.copy(logger = testLogger))

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
          _      <- LogLevel.Debug(ZIO.log("It's alive!"))
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Debug)
      },
      test("log at a different log level") {
        for {
          _      <- ZIO.logDebug("It's alive!")
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Debug)
      },
      test("log at a different log level") {
        for {
          _      <- ZIO.logDebug("It's alive!")
          output <- logOutput
        } yield assertTrue(output.length == 1) &&
          assertTrue(output(0).message() == "It's alive!") &&
          assertTrue(output(0).logLevel == LogLevel.Debug)
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
          _      <- ZIO.debug(output(0).call(ZLogger.defaultFormatter))
        } yield assertTrue(true)
      }
    ) @@ sequential @@ after(clearOutput)
}
