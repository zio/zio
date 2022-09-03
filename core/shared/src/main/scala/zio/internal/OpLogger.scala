package zio.internal
import zio._

import zio.stacktracer.TracingImplicits.disableAutoTrace

private[internal] trait OpLogger {
  def logAsk[A](f: Unsafe => (FiberRuntime[_, _], Fiber.Status) => A)(trace: Trace, logTrace: Trace): Unit
  def logTell(message: FiberMessage)(trace: Trace, logTrace: Trace): Unit
  def logRunLoop(
    message: String,
    effect: ZIO[Any, Any, Any],
    currentDepth: Int,
    localStack: Chunk[ZIO.EvaluationStep],
    runtimeFlags0: RuntimeFlags
  )(implicit trace: Trace, logTrace: Trace): Unit
  def logInterruptChild(signal: FiberMessage.InterruptSignal, fiberRuntime: FiberRuntime[_, _])(
    trace: Trace,
    logTrace: Trace
  ): Unit
  def logInterrupt(cause: Cause[Any])(trace: Trace, logTrace: Trace): Unit
  def logMessage(message: String)(trace: Trace, logTrace: Trace): Unit
  def logEffect(effect: Any)(trace: Trace, logTrace: Trace): Unit
}

object OpLogger {
  private[internal] final case class DefaultOpLogger(
    logFn: (() => String, zio.Cause[Any], Option[zio.LogLevel], Trace) => Unit
  ) extends OpLogger {

    import zio.internal.stacktracer.Tracer.newTrace
    val traceLevel = Some(LogLevel.Trace)

    override def logAsk[A](f: Unsafe => (FiberRuntime[_, _], Fiber.Status) => A)(trace: Trace, logTrace: Trace): Unit =
      logFn(() => s"Oplog: ${logTrace.toString()} f", Cause.empty, traceLevel, trace)

    override def logTell(message: FiberMessage)(trace: Trace, logTrace: Trace): Unit =
      logFn(() => s"OpLog: ${logTrace.toString()} $message", Cause.empty, traceLevel, trace)

    override def logRunLoop(
      message: String,
      effect: ZIO[Any, Any, Any],
      currentDepth: Int,
      localStack: Chunk[ZIO.EvaluationStep],
      runtimeFlags0: RuntimeFlags
    )(implicit trace: Trace, logTrace: Trace): Unit =
      logFn(
        () => s"Oplog: ${logTrace.toString} $message, effect, currentDepth, localStack runtimeFlags0",
        Cause.empty,
        traceLevel,
        trace
      )
    def logInterruptChild(signal: FiberMessage.InterruptSignal, fiberRuntime: FiberRuntime[_, _])(
      trace: Trace,
      logTrace: Trace
    ): Unit = logFn(
      () => s"Oplog: ${logTrace.toString} , signal, fiberRuntime",
      Cause.empty,
      traceLevel,
      trace
    )
    def logInterrupt(cause: Cause[Any])(trace: Trace, logTrace: Trace): Unit =
      logFn(() => s"Oplog: ${logTrace.toString} , cause", cause, traceLevel, trace)
    def logMessage(message: String)(trace: Trace, logTrace: Trace): Unit =
      logFn(() => s"Oplog: ${logTrace.toString} , message", Cause.empty, traceLevel, trace)
    def logEffect(effect: Any)(trace: Trace, logTrace: Trace): Unit =
      logFn(() => s"Oplog: ${logTrace.toString} , effect", Cause.empty, traceLevel, trace)
  }

}
