package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

trait ZLogger[-Message, +Output] { self =>
  def apply(
    trace: ZTraceElement,
    fiberId: FiberId.Runtime,
    logLevel: LogLevel,
    message: () => Message,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan]
  ): Output

  /**
   * Combines this logger with the specified logger to produce a new logger
   * that logs to both this logger and that logger.
   */
  def ++[M <: Message, O](
    that: ZLogger[M, O]
  )(implicit zippable: Zippable[Output, O]): ZLogger[M, zippable.Out] =
    new ZLogger[M, zippable.Out] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId.Runtime,
        logLevel: LogLevel,
        message: () => M,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): zippable.Out =
        zippable.zip(
          self(trace, fiberId, logLevel, message, context, spans),
          that(trace, fiberId, logLevel, message, context, spans)
        )
    }

  /**
   * Returns a version of this logger that only logs messages when the log
   * level satisfies the specified predicate.
   */
  final def filterLogLevel(f: LogLevel => Boolean): ZLogger[Message, Option[Output]] =
    new ZLogger[Message, Option[Output]] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId.Runtime,
        logLevel: LogLevel,
        message: () => Message,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): Option[Output] =
        if (f(logLevel)) {
          Some(self(trace, fiberId, logLevel, message, context, spans))
        } else None
    }

  final def map[B](f: Output => B): ZLogger[Message, B] =
    new ZLogger[Message, B] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId.Runtime,
        logLevel: LogLevel,
        message: () => Message,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): B = f(self(trace, fiberId, logLevel, message, context, spans))
    }
}
object ZLogger {
  val defaultFormatter: ZLogger[String, String] = (
    trace: ZTraceElement,
    fiberId: FiberId.Runtime,
    logLevel: LogLevel,
    message0: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans0: List[LogSpan]
  ) => {
    val sb = new StringBuilder()

    val _ = context

    val now = java.time.Instant.now()

    val nowMillis = java.lang.System.currentTimeMillis()

    sb.append("timestamp=")
      .append(now.toString())
      .append(" level=")
      .append(logLevel.label)
      .append(" thread=#")
      .append(fiberId.id.toString)
      .append(" message=\"")
      .append(message0())
      .append("\"")

    if (spans0.nonEmpty) {
      sb.append(" ")

      val it    = spans0.iterator
      var first = true

      while (it.hasNext) {
        if (first) {
          first = false
        } else {
          sb.append(" ")
        }

        it.next().unsafeRender(sb, nowMillis)
      }
    }

    trace match {
      case ZTraceElement.SourceLocation(location, file, line, column) =>
        sb.append(" location=")

        appendQuoted(location, sb)

        sb.append(" file=")

        appendQuoted(file, sb)

        sb.append(" line=")
          .append(line)

        sb.append(" column=")
          .append(column)

      case _ =>
    }

    sb.toString()
  }

  /**
   * A logger that does nothing in response to logging events.
   */
  val none: ZLogger[Any, Unit] = new ZLogger[Any, Unit] {
    def apply(
      trace: ZTraceElement,
      fiberId: FiberId.Runtime,
      logLevel: LogLevel,
      message: () => Any,
      context: Map[FiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan]
    ): Unit =
      ()
  }

  private def appendQuoted(label: String, sb: StringBuilder): StringBuilder = {
    if (label.indexOf(" ") < 0) sb.append(label)
    else sb.append("\"").append(label).append("\"")
    sb
  }
}
