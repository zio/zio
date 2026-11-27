package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `ZLogger` turns a log event into a value of type `Output`. An event
 * consists of the source location, the id of the logging fiber, the log level,
 * the message, the cause, the fiber's `FiberRefs`, the active log spans and the
 * log annotations. A logger is a plain value: loggers can be combined, filtered
 * and transformed, and the runtime calls each registered logger once for every
 * log statement.
 *
 * {{{
 * val length: ZLogger[String, Int]   = ZLogger.simple[String, Int](_.length)
 * val upper: ZLogger[String, String] = ZLogger.simple[String, String](_.toUpperCase)
 *
 * val both: ZLogger[String, (Int, String)] = length ++ upper
 * val result: (Int, String)                = both.test("hello")
 * // (5,HELLO)
 * }}}
 *
 * @tparam Message
 *   The type of the message the logger accepts.
 * @tparam Output
 *   The type of the value the logger returns for each event.
 * @see
 *   [[https://zio.dev/reference/observability/logging/zlogger/ ZLogger]]
 * @see
 *   [[https://zio.dev/reference/observability/logging/ Logging]]
 */
trait ZLogger[-Message, +Output] { self =>

  /**
   * Processes one log event and returns the output.
   *
   * @param trace
   *   The source location of the log statement.
   * @param fiberId
   *   The id of the fiber that logged.
   * @param logLevel
   *   The level of the log statement.
   * @param message
   *   A thunk that returns the message; the logger evaluates it when it needs
   *   the message.
   * @param cause
   *   The cause attached to the log statement; `Cause.empty` when there is
   *   none.
   * @param context
   *   The `FiberRefs` of the logging fiber.
   * @param spans
   *   The active log spans of the logging fiber.
   * @param annotations
   *   The active log annotations of the logging fiber, as key-value pairs.
   */
  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Output

  /**
   * Combines this logger with the specified logger to produce a new logger that
   * logs to both this logger and that logger. Both loggers run for every event,
   * and the outputs are zipped: for outputs `Int` and `String` the new logger
   * returns a `(Int, String)`, and a `Unit` output is dropped.
   */
  def ++[M <: Message, O](
    that: ZLogger[M, O]
  )(implicit zippable: Zippable[Output, O]): ZLogger[M, zippable.Out] =
    new ZLogger[M, zippable.Out] {
      def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): zippable.Out =
        zippable.zip(
          self(trace, fiberId, logLevel, message, cause, context, spans, annotations),
          that(trace, fiberId, logLevel, message, cause, context, spans, annotations)
        )
    }

  /**
   * Combines this logger with `that` like `++`, and returns only the output of
   * `that`. Both loggers run for every event.
   */
  def +>[M <: Message, O](that: ZLogger[M, O]): ZLogger[M, O] = (self ++ that).map(_._2)

  /**
   * Combines this logger with `that` like `++`, and returns only the output of
   * this logger. Both loggers run for every event.
   */
  def <+[M <: Message](that: ZLogger[M, Any]): ZLogger[M, Output] = (self ++ that).map(_._1)

  /**
   * Returns a logger that accepts messages of type `Message1`. It applies `f`
   * to the message when this logger evaluates it, and passes the result to this
   * logger.
   */
  final def contramap[Message1](f: Message1 => Message): ZLogger[Message1, Output] =
    new ZLogger[Message1, Output] {
      def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message1,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Output = self(trace, fiberId, logLevel, () => f(message()), cause, context, spans, annotations)
    }

  /**
   * Returns a version of this logger that only logs messages when the log level
   * satisfies the specified predicate. The new logger returns `Some` with the
   * output of this logger when `f` returns `true` for the level, and `None`
   * without running this logger otherwise.
   */
  final def filterLogLevel(f: LogLevel => Boolean): ZLogger[Message, Option[Output]] =
    new ZLogger[Message, Option[Output]] {
      def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Option[Output] =
        if (f(logLevel)) {
          Some(self(trace, fiberId, logLevel, message, cause, context, spans, annotations))
        } else None
    }

  /**
   * Returns a logger whose output is the output of this logger transformed by
   * `f`.
   */
  final def map[B](f: Output => B): ZLogger[Message, B] =
    new ZLogger[Message, B] {
      def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): B = f(self(trace, fiberId, logLevel, message, cause, context, spans, annotations))
    }

  /**
   * Runs this logger on `input` and returns the output. The event has the
   * `Info` level, an empty trace, `FiberId.None`, an empty cause, empty
   * `FiberRefs`, no log spans and no annotations. It is meant for trying a
   * logger without running a fiber.
   */
  final def test(input: => Message): Output =
    apply(
      Trace.empty,
      FiberId.None,
      LogLevel.Info,
      () => input,
      Cause.empty,
      FiberRefs.empty,
      Nil,
      Map.empty
    )
}

/** The predefined loggers and the constructors for simple ones. */
object ZLogger {
  private[zio] val stringTag: LightTypeTag = EnvironmentTag[String].tag
  private[zio] val causeTag: LightTypeTag  = EnvironmentTag[Cause[Any]].tag

  /**
   * A logger that formats an event as one line of space-separated `key=value`
   * pairs and returns the line as a string. The line has these parts, in this
   * order:
   *
   *   - `timestamp=`, the current time as an ISO-8601 instant;
   *   - `level=`, the label of the level;
   *   - `thread=#`, followed by the name of the fiber's thread;
   *   - `message=`, followed by the message;
   *   - `cause=`, followed by the pretty-printed cause, only when the cause is
   *     not empty;
   *   - each log span as `label=<elapsed>ms`;
   *   - `location=`, `file=` and `line=`, only when the trace has a location;
   *   - each annotation as `key=value`.
   *
   * The logger only formats the event; it does not print it.
   *
   * {{{
   * val line: String = ZLogger.default.test("hi there")
   * }}}
   */
  val default: ZLogger[String, String] = (
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message0: () => String,
    cause: Cause[Any],
    _: FiberRefs,
    spans0: List[LogSpan],
    annotations: Map[String, String]
  ) => {
    // For why 256 here, see https://github.com/zio/zio/pull/9416#discussion_r1886208534
    val sb = new StringBuilder(256)

    val now = java.time.Instant.now()

    sb.append("timestamp=")
      .append(now.toString)
      .append(" level=")
      .append(logLevel.label)
      .append(" thread=#")

    fiberId
      .threadNameInto(sb)(Unsafe)
      .append(" message=\"")
      .append(message0())
      .append('"')

    if ((cause ne null) && (cause ne Cause.empty)) {
      sb.append(" cause=\"")
        .append(cause.prettyPrint)
        .append('"')
    }

    if (spans0.nonEmpty) {
      val nowMillis = now.toEpochMilli

      sb.append(' ')

      val it = spans0.iterator

      it.next().renderInto(sb, nowMillis)(Unsafe)
      while (it.hasNext) {
        sb.append(' ')
        it.next().renderInto(sb, nowMillis)(Unsafe)
      }
    }

    val parsedTrace = Trace.parseOrNull(trace)
    if (parsedTrace ne null) {
      sb.append(" location=")
      appendQuoted(parsedTrace.location, sb)
      sb.append(" file=")
      appendQuoted(parsedTrace.file, sb)
      sb.append(" line=").append(parsedTrace.line)
    }

    if (annotations.nonEmpty) {
      sb.append(' ')

      val it    = annotations.iterator
      var first = true

      while (it.hasNext) {
        if (first) {
          first = false
        } else {
          sb.append(' ')
        }

        val kv = it.next()

        appendQuoted(kv._1, sb)
        sb.append('=')
        appendQuoted(kv._2, sb)
      }
    }

    sb.toString()
  }

  /**
   * A logger that does nothing in response to logging events.
   */
  val none: ZLogger[Any, Unit] = new ZLogger[Any, Unit] {
    def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Any,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Unit =
      ()
  }

  /**
   * Creates a logger that returns `log` applied to the message and ignores the
   * other fields of the event. The message is evaluated for every event.
   */
  def simple[A, B](log: A => B): ZLogger[A, B] =
    new ZLogger[A, B] {
      def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => A,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): B =
        log(message())
    }

  /**
   * Creates a logger that ignores the event and returns `a`. The by-name
   * argument is evaluated again for every event.
   */
  def succeed[A](a: => A): ZLogger[Any, A] = simple(_ => a)

  private def appendQuoted(label: String, sb: StringBuilder): StringBuilder = {
    if (label.indexOf(' ') < 0) sb.append(label)
    else sb.append('"').append(label).append('"')
    sb
  }
}
