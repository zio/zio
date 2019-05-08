package scalaz.zio

import scalaz.zio.internal.tracing.FiberAncestry
import scalaz.zio.stacktracer.SourceLocation

final case class ZTrace(
  fiberId: FiberId,
  executionTrace: List[SourceLocation],
  stackTrace: List[SourceLocation],
  fiberAncestry: FiberAncestry
) {
  final def prettyPrint: String = {
    val execPrint = s"Fiber:$fiberId ZIO Execution trace:" ::
      executionTrace.reverse.map(loc => " at " + loc.prettyPrint)
    val stackPrint = "" :: s"Fiber:$fiberId was supposed to continue to:" ::
      stackTrace.reverse.map(loc => s" a future continuation at " + loc.prettyPrint)
    val ancestry = fiberAncestry.parentTrace.map(trace => s"\nFiber:$fiberId was spawned by:\n\n" + trace.prettyPrint)

    (execPrint ++ stackPrint ++ ancestry).mkString("\n")
  }
}
