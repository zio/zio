package zio

import zio.internal.Blocking

private[zio] trait RuntimePlatformSpecific {

  val defaultYieldOpCount: Int =
    2048

  val defaultReportFatal: Throwable => Nothing =
    (t: Throwable) => {
      t.printStackTrace()
      try {
        java.lang.System.exit(-1)
        throw t
      } catch { case _: Throwable => throw t }
    }

  val defaultSupervisors: Set[Supervisor[Any]] =
    Set.empty

  val defaultFlags: Set[RuntimeConfigFlag] =
    Set(RuntimeConfigFlag.EnableFiberRoots)

  val defaultLoggers: Set[ZLogger[String, Any]] =
    Set(ZLogger.default.map(println(_)).filterLogLevel(_ >= LogLevel.Info))

  val defaultFatal: Set[Class[_ <: Throwable]] =
    Set(classOf[VirtualMachineError])

  val defaultExecutor: Executor =
    Executor.makeDefault(defaultYieldOpCount)

  val defaultBlockingExecutor: Executor =
    Blocking.blockingExecutor
}
