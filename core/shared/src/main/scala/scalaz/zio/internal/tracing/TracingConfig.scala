package scalaz.zio.internal.tracing

/**
 * Toggles:
 *
 * @param traceExecution Collect traces of most ZIO operations into a Full Execution Trace
 *
 * @param traceEffectOpsInExecution Add traces of ZIO.effect* operations in Full Execution Trace. Applies when
 *                                  `traceExecution` is enabled. May multiply the amount of memory used by the
 *                                  tracing cache.
 *
 * @param traceStack Collect trace of the current stack of future continuations,
 *                   This trace resembles an imperative stacktrace and will usually include similar information,
 *                   but due to the way ZIO tracing works, it includes only references to *future continuations*,
 *                   i.e. the *end* of a "stack frame" rather than the start of a "stack frame".
 *
 * @param executionTraceLength Preserve how many lines of a full execution trace
 *
 * @param stackTraceLength Maximum length of a stack trace
 *
 * @param ancestorExecutionTraceLength How many lines of execution trace to include in the
 *                                     trace of last lines before .fork in the parent fiber
 *                                     that spawned the current fiber
 *
 * @param ancestorStackTraceLength How many lines of stack trace to include in the
 *                                 trace of last lines before .fork in the parent fiber
 *                                 that spawned the current fiber
 */
final case class TracingConfig(
  traceExecution: Boolean,
  traceEffectOpsInExecution: Boolean,
  traceStack: Boolean,
  executionTraceLength: Int,
  stackTraceLength: Int,
  ancestorExecutionTraceLength: Int,
  ancestorStackTraceLength: Int
)

object TracingConfig {
  final def enabled   = TracingConfig(true, true, true, 100, 100, 10, 10)
  final def stackOnly = TracingConfig(false, false, true, 100, 100, 10, 10)
  final def disabled  = TracingConfig(false, false, false, 0, 0, 0, 0)
}
