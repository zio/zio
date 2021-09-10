package zio

import zio.internal.stacktracer._

trait ZLogger[+A] { self =>
  def apply(
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan]
  ): A

  /**
   * Combines this logger with the specified logger to produce a new logger
   * that logs to both this logger and that logger.
   */
  def ++[B](that: ZLogger[B])(implicit zippable: Zippable[A, B]): ZLogger[zippable.Out] =
    new ZLogger[zippable.Out] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
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
  final def filterLogLevel(f: LogLevel => Boolean): ZLogger[Option[A]] =
    new ZLogger[Option[A]] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): Option[A] =
        if (f(logLevel)) {
          Some(self(trace, fiberId, logLevel, message, context, spans))
        } else None
    }

  final def map[B](f: A => B): ZLogger[B] =
    new ZLogger[B] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): B = f(self(trace, fiberId, logLevel, message, context, spans))
    }
}
object ZLogger {
  val defaultFormatter: ZLogger[String] = (
    trace: ZTraceElement,
    fiberId: FiberId,
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
      .append(fiberId.seqNumber.toString)
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
      case ZTraceElement.NoLocation(_) =>

      case ZTraceElement.SourceLocation(file, clazz, method, line) =>
        sb.append(" file=")

        appendQuoted(file, sb)

        sb.append(" line=")
          .append(line)
          .append(" class=")

        appendQuoted(clazz, sb)

        sb.append(" method=")
          .append(method)
    }

    sb.toString()
  }

  private def appendQuoted(label: String, sb: StringBuilder): StringBuilder = {
    if (label.indexOf(" ") < 0) sb.append(label)
    else sb.append("\"").append(label).append("\"")
    sb
  }
}
