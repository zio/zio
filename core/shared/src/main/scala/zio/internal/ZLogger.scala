package zio.internal

import zio._
import zio.internal.stacktracer._

trait ZLogger[+A] { self =>
  def apply(
    trace: ZTraceElement,
    fiberId: Fiber.Id,
    logLevel: LogLevel,
    message: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan]
  ): A

  final def logged(f: A => Unit): ZLogger[Unit] =
    new ZLogger[Unit] {
      def apply(
        trace: ZTraceElement,
        fiberId: Fiber.Id,
        logLevel: LogLevel,
        message: () => String,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): Unit = f(self(trace, fiberId, logLevel, message, context, spans))
    }
}
object ZLogger {
  val defaultFormatter: ZLogger[String] = (
    trace: ZTraceElement,
    fiberId: Fiber.Id,
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

        val span = it.next()

        sb.append(span.render(nowMillis))
      }
    }

    trace match {
      case ZTraceElement.NoLocation(_) =>

      case ZTraceElement.SourceLocation(file, clazz, method, line) =>
        sb.append(" ")
          .append("file=\"")
          .append(file)
          .append("\"")
          .append("line=")
          .append(line)
          .append("class=")
          .append(clazz)
          .append("method=")
          .append(method)
    }

    sb.toString()
  }
}
