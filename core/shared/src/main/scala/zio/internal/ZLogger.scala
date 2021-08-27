package zio.internal

import zio._

trait ZLogger[+A] { self =>
  def apply(
    fiberId: Fiber.Id,
    logLevel: LogLevel,
    message: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan]
  ): A

  final def logged(f: A => Unit): ZLogger[Unit] =
    new ZLogger[Unit] {
      def apply(
        fiberId: Fiber.Id,
        logLevel: LogLevel,
        message: () => String,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan]
      ): Unit = f(self(fiberId, logLevel, message, context, spans))
    }
}
object ZLogger {
  val defaultFormatter: ZLogger[String] = (
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

    sb.append(now.toString())
      .append(" level=")
      .append(logLevel.label)
      .append(" thread=")
      .append(fiberId.toString)
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

    sb.toString()
  }
}
