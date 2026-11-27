package zio.test

import zio._

import java.util.concurrent.atomic.AtomicReference

/**
 * A `ZTestLogger` is an implementation of a `ZLogger` that writes all log
 * messages to an internal data structure. The contents of this data structure
 * can be accessed using the `logOutput` operator, so a test can check which
 * messages were logged. The ZIO Test runner installs a new `ZTestLogger` for
 * each test.
 *
 * {{{
 * import zio.{LogLevel, ZIO}
 * import zio.test._
 *
 * val loggingTest = test("logging works") {
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
   * Returns the contents of the log, oldest entry first.
   */
  def logOutput: UIO[Chunk[ZTestLogger.LogEntry]]
}

/**
 * Constructors and accessors for [[ZTestLogger]].
 */
object ZTestLogger {

  /**
   * A layer which constructs a new `ZTestLogger` and runs the effect it is
   * provided to with the `ZTestLogger` added to its loggers.
   */
  val default: ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      for {
        testLogger <- ZTestLogger.make
        _          <- FiberRef.currentLoggers.locallyScopedWith(_ + testLogger)
      } yield ()
    }

  /**
   * Accesses the contents of the current test logger. Dies with a
   * `RuntimeException` when no `ZTestLogger` is installed.
   */
  val logOutput: UIO[Chunk[ZTestLogger.LogEntry]] =
    ZIO.loggersWith { loggers =>
      loggers.collectFirst { case testLogger: ZTestLogger[_, _] => testLogger.logOutput }
        .getOrElse(ZIO.dieMessage("Defect: ZTestLogger is missing"))
    }

  private[test] def locally[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    FiberRef.currentLoggers.locallyWith(_ + unsafe.make()(Unsafe))(zio)

  /**
   * A log entry captures all of the contents of a log message as a data
   * structure.
   */
  final case class LogEntry(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ) {

    /**
     * Passes the contents of this entry to `zlogger` and returns its result.
     */
    def call[A](zlogger: ZLogger[String, A]): A =
      zlogger(trace, fiberId, logLevel, message, cause, context, spans, annotations)
  }

  /**
   * Constructs a `ZTestLogger`.
   */
  private def make: UIO[ZLogger[String, Unit]] =
    ZIO.succeed(unsafe.make()(Unsafe))

  private[test] object unsafe {
    def make()(implicit unsafe: Unsafe): ZLogger[String, Unit] =
      new TestLogger

    private final class TestLogger
        extends AtomicReference[Chunk[LogEntry]](Chunk.empty)
        with ZTestLogger[String, Unit] {
      def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Unit = {
        val newEntry = LogEntry(trace, fiberId, logLevel, message, cause, context, spans, annotations)

        var updated = false
        while (!updated) {
          val oldState = get
          updated = compareAndSet(oldState, oldState :+ newEntry)
        }
      }
      val logOutput: UIO[Chunk[LogEntry]] =
        ZIO.succeed(get)
    }
  }
}
