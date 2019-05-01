package scalaz.zio.internal.tracing

import scalaz.zio.stacktracer.{SourceExtractor, SourceLocationCache}

trait Tracer {

  val extractor: SourceExtractor
  val cache: SourceLocationCache

  def maxTraceLength: Int
  def fiberAncestorTraceLength: Int

  /**
   * Emulate imperative stack traces.
   *
   * Collect only trace of the *current stack*, rather than a full execution trace
   * */
  def traceLeftBindsOnly: Boolean

//  final val fiberAncestorTraceLength = 10
//
//  /**
//   * Emulate JVM Native stack-trace experience.
//   *
//   * Provides only *stack* trace, rather than a full execution trace
//   * */
//  final val traceLeftBindsOnly = false
}
