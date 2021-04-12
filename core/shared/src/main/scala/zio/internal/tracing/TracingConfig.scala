/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal.tracing

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
 * @param ancestryLength Maximum count of parent fiber traces to include
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
  ancestryLength: Int,
  ancestorExecutionTraceLength: Int,
  ancestorStackTraceLength: Int
)

object TracingConfig {
  def enabled: TracingConfig   = TracingConfig(true, true, true, 100, 100, 10, 10, 10)
  def stackOnly: TracingConfig = TracingConfig(false, false, true, 100, 100, 10, 10, 10)
  def disabled: TracingConfig  = TracingConfig(false, false, false, 0, 0, 0, 0, 10)
}
