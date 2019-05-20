package scalaz.zio

import scalaz.zio.internal.stacktracer.ZTraceElement

final case class ZTrace(
  fiberId: FiberId,
  executionTrace: List[ZTraceElement],
  stackTrace: List[ZTraceElement],
  parentTrace: Option[ZTrace]
) {
  final def prettyPrint: String = {
    val execTrace  = executionTrace.nonEmpty
    val stackTrace = this.stackTrace.nonEmpty

    val stackPrint =
      if (stackTrace)
        s"Fiber:$fiberId was supposed to continue to:" ::
          this.stackTrace.reverse.map(loc => s"  a future continuation at " + loc.prettyPrint)
      else
        s"Fiber:$fiberId was supposed to continue to: <empty trace>" :: Nil

    val execPrint =
      if (execTrace)
        s"Fiber:$fiberId execution trace:" ::
          executionTrace.reverse.map(loc => "  at " + loc.prettyPrint)
      else s"Fiber:$fiberId ZIO Execution trace: <empty trace>" :: Nil

    val ancestry: List[String] =
      parentTrace.map { trace =>
        s"Fiber:$fiberId was spawned by:\n" :: trace.prettyPrint :: Nil
      }.getOrElse(s"Fiber:$fiberId was spawned by: <empty trace>" :: Nil)

    (stackPrint ++ ("" :: execPrint) ++ ("" :: ancestry)).mkString("\n")
  }
}
