package zio

import scala.concurrent.ExecutionContext

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

  val defaultLoggers: Set[ZLogger[String, Any]] =
    Set(ZLogger.default.map(println(_)).filterLogLevel(_ >= LogLevel.Info))

  val defaultFatal: Set[Class[_ <: Throwable]] =
    Set.empty

  val defaultExecutor: Executor =
    Executor.fromExecutionContext(defaultYieldOpCount)(ExecutionContext.global)

  val defaultBlockingExecutor: Executor =
    defaultExecutor
}
