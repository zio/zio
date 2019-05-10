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
      else Nil

    val stackPrint =
      if (stackTrace)
        (if (execTrace) "" :: (_: List[String]) else ZIO.identityFn[List[String]]) {
          s"Fiber:$fiberId was supposed to continue to:" ::
            this.stackTrace.reverse.map(loc => s"  a future continuation at " + loc.prettyPrint)
        }
      else Nil

    val ancestry: List[String] =
      (if (execTrace || stackTrace) "" :: (_: List[String]) else ZIO.identityFn[List[String]]) {
        fiberAncestry.parentTrace.map {
          trace => s"Fiber:$fiberId was spawned by:\n" :: trace.prettyPrint :: Nil
        }.getOrElse(s"Fiber was Fiber:$fiberId" :: Nil)
    }

    (execPrint ++ stackPrint ++ ancestry).mkString("\n")
  }
}
