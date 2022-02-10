package zio.test

import zio._

import scala.annotation.tailrec

/**
 * A `ZTestLogger` is an implementation of a `ZLogger` that writes all log
 * messages to an internal data structure. The contents of this data structure
 * can be accessed using the `logOutput` operator. This makes it easy to write
 * tests to verify that expected messages are being logged.
 *
 * {{{
 * test("logging works") {
 *   for {
 *     _      <- ZIO.logDebug("It's alive!")
 *     output <- ZTestLogger.logOutput
 *   } yield assertTrue(output.length == 1) &&
 *     assertTrue(output(0).message() == "It's alive!") &&
 *     assertTrue(output(0).logLevel == LogLevel.Debug)
 * }
 * }}}
 */
sealed trait ZTestLogger[-Message, +Output] extends ZLogger[Message, Output] {

  /**
   * Returns the contents of the log.
   */
  def logOutput: UIO[Chunk[ZTestLogger.LogEntry]]
}

object ZTestLogger {

  /**
   * A layer which constructs a new `ZTestLogger` and runs the effect it is
   * provided to with the `RuntimeConfig` updated to add the `ZTestLogger`.
   */
  val default: ZLayer[Any, Nothing, Any] =
    ZLayer {
      for {
        runtimeConfig              <- ZManaged.runtimeConfig
        testLoggers                <- ZTestLogger.make.toManaged
        (stringLogger, causeLogger) = testLoggers
        runtimeConfigAspect         = RuntimeConfigAspect.addLogger(stringLogger) >>> RuntimeConfigAspect.addLogger(causeLogger)
        _                          <- ZManaged.withRuntimeConfig(runtimeConfigAspect(runtimeConfig))
      } yield ()
    }

  /**
   * Accesses the contents of the current test logger.
   */
  val logOutput: UIO[Chunk[ZTestLogger.LogEntry]] =
    ZIO.runtimeConfig.flatMap { runtimeConfig =>
      runtimeConfig.loggers
        .getAll[String]
        .collectFirst { case testLogger: ZTestLogger[_, _] =>
          testLogger.logOutput
        }
        .getOrElse(ZIO.dieMessage("Defect: ZTestLogger is missing"))
    }

  /**
   * A log entry captures all of the contents of a log message as a data
   * structure.
   */
  final case class LogEntry(
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan],
    location: ZTraceElement,
    annotations: Map[String, String]
  ) {
    def call[A](zlogger: ZLogger[String, A]): A =
      zlogger(trace, fiberId, logLevel, message, context, spans, location, annotations)
  }

  /**
   * Constructs a `ZTestLogger`.
   */
  private def make: UIO[(ZLogger[String, Unit], ZLogger[Cause[Any], Unit])] =
    ZIO.succeed {

      val _logOutput = new java.util.concurrent.atomic.AtomicReference[Chunk[LogEntry]](Chunk.empty)

      val stringLogger: ZTestLogger[String, Unit] =
        new ZTestLogger[String, Unit] {
          @tailrec
          def apply(
            trace: ZTraceElement,
            fiberId: FiberId,
            logLevel: LogLevel,
            message: () => String,
            context: Map[FiberRef.Runtime[_], AnyRef],
            spans: List[LogSpan],
            location: ZTraceElement,
            annotations: Map[String, String]
          ): Unit = {
            val newEntry = LogEntry(trace, fiberId, logLevel, message, context, spans, location, annotations)

            val oldState = _logOutput.get

            if (!_logOutput.compareAndSet(oldState, oldState :+ newEntry))
              apply(trace, fiberId, logLevel, message, context, spans, location, annotations)
            else ()
          }
          val logOutput: UIO[Chunk[LogEntry]] =
            UIO(_logOutput.get)
        }

      val causeLogger: ZLogger[Cause[Any], Unit] =
        stringLogger.contramap((cause: Cause[Any]) => cause.prettyPrint)

      (stringLogger, causeLogger)
    }
}
