package scalaz.zio

import scalaz.zio.internal.stacktracer.ZTraceElement
import scalaz.zio.internal.tracing.FiberAncestry

final case class ZTrace(
  fiberId: FiberId,
  executionTrace: List[ZTraceElement],
  stackTrace: List[ZTraceElement],
  fiberAncestry: FiberAncestry
) {
  final def prettyPrint: String = {
    val execTrace = executionTrace.nonEmpty
    val stackTrace = this.stackTrace.nonEmpty

    val execPrint =
      if (execTrace)
        s"Fiber:$fiberId ZIO Execution trace:" ::
          executionTrace.reverse.map(loc => "  at " + loc.prettyPrint)
      else s"Fiber:$fiberId ZIO Execution trace: <empty trace>" :: Nil

    val stackPrint =
      if (stackTrace)
        s"\nFiber:$fiberId was supposed to continue to:" ::
          this.stackTrace.reverse.map(loc => s"  a future continuation at " + loc.prettyPrint)
      else
        s"\nFiber:$fiberId was supposed to continue to: <empty trace>" ::Nil

    val ancestry: List[String] =
      fiberAncestry.parentTrace.map {
        trace => s"\nFiber:$fiberId was spawned by:\n" :: trace.prettyPrint :: Nil
      }.getOrElse(s"\nFiber:$fiberId was spawned by: <empty trace>" :: Nil)

    (execPrint ++ stackPrint ++ ancestry).mkString("\n")
  }
}
