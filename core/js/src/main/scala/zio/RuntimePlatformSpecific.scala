package zio

import scala.concurrent.ExecutionContext
import scala.scalajs.js.Dynamic.{global => jsglobal}

private[zio] trait RuntimePlatformSpecific {

  val defaultYieldOpCount: Int =
    2048

  val defaultReportFatal: Throwable => Nothing =
    (t: Throwable) => {
      t.printStackTrace()
      throw t
    }

  val defaultSupervisors: Set[Supervisor[Any]] =
    Set.empty

  val defaultFlags: Set[RuntimeConfigFlag] =
    Set(RuntimeConfigFlag.EnableFiberRoots)

  val defaultLoggers: Set[ZLogger[String, Any]] = {
    val logger: ZLogger[String, Unit] =
      (
        trace: Trace,
        fiberId: FiberId,
        level: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: Map[FiberRef[_], Any],
        spans: List[LogSpan],
        annotations: Map[String, String]
      ) => {
        try {
          // TODO: Improve output & use console.group for spans, etc.
          val line = ZLogger.default(trace, fiberId, level, message, cause, context, spans, annotations)

          if (level == LogLevel.Fatal) jsglobal.console.error(line)
          else if (level == LogLevel.Error) jsglobal.console.error(line)
          else if (level == LogLevel.Debug) jsglobal.console.debug(line)
          else jsglobal.console.log(line)

          ()
        } catch {
          case t => ()
        }
      }

    Set(logger)
  }

  val defaultFatal: Set[Class[_ <: Throwable]] =
    Set.empty

  val defaultExecutor: Executor =
    Executor.fromExecutionContext(defaultYieldOpCount)(ExecutionContext.global)

  val defaultBlockingExecutor: Executor =
    defaultExecutor
}
