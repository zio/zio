/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

package zio

import zio.internal.LoomSupport.LoomNotAvailableException
import zio.internal.{Blocking, IsFatal, LoomSupport}
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The platform's default runtime settings, which the [[Runtime]] companion
 * object inherits.
 */
private[zio] trait RuntimePlatformSpecific {

  /**
   * The executor that runs fibers by default. On the JVM and Scala Native it is
   * ZIO's work-stealing scheduler, with one worker thread per available
   * processor; on Scala.js it runs each task as a JavaScript macrotask through
   * `MacrotaskExecutor`.
   */
  final val defaultExecutor: Executor =
    Executor.makeDefault(autoBlocking = false)

  /**
   * The executor that runs blocking effects, such as `ZIO.attemptBlocking`, by
   * default. On the JVM and Scala Native it is a thread pool that starts a
   * daemon thread named `zio-default-blocking-N` whenever no idle thread is
   * free, with no upper limit, and stops threads that stay idle for 60
   * seconds. On Scala.js, which has a single thread, it is `defaultExecutor`.
   */
  final val defaultBlockingExecutor: Executor =
    Blocking.blockingExecutor

  /**
   * An `IsFatal` that matches no error. Kept for binary compatibility: which
   * errors count as fatal is no longer configurable.
   */
  @deprecated("IsFatal is deprecated, kept only for binary compatability.", "2.1.21")
  final val defaultFatal: IsFatal = IsFatal.empty

  /**
   * The loggers of a new runtime: one logger that writes the entries of level
   * `Info` and above in the format of `ZLogger.default` (`timestamp=...
   * level=INFO thread=#zio-fiber-... message="..."`). On the JVM and Scala
   * Native it prints them to standard output. On Scala.js it writes them to
   * the JavaScript console, with `console.error` for `Error` and `Fatal`
   * entries and `console.log` for the others, and ignores exceptions thrown
   * while logging.
   */
  final val defaultLoggers: Set[ZLogger[String, Any]] =
    Set(ZLogger.default.map(println(_)).filterLogLevel(_ >= LogLevel.Info))

  /**
   * The function that the runtime calls by default with a fatal error. On the
   * JVM it prints the stack trace, calls `java.lang.System.exit(-1)` and throws
   * the error if the program does not end. On Scala.js and Scala Native it
   * prints the stack trace and throws the error.
   */
  final val defaultReportFatal: Throwable => Nothing =
    (t: Throwable) => {
      t.printStackTrace()
      try {
        java.lang.System.exit(-1)
        throw t
      } catch { case _: Throwable => throw t }
    }

  /**
   * The supervisor of a new runtime: `Supervisor.none`.
   */
  final val defaultSupervisor: Supervisor[Any] =
    Supervisor.none

  /**
   * A layer that makes an executor that runs each task on a new virtual
   * thread the executor of the effects it is provided to, as
   * `Runtime.setExecutor` does (JVM only). The layer fails with a
   * `LoomSupport.LoomNotAvailableException` ("Loom API not available") on
   * Java versions before 21. It shares one executor instance with
   * `enableLoomBasedBlockingExecutor`.
   */
  def enableLoomBasedExecutor(implicit trace: Trace): ZLayer[Any, LoomNotAvailableException, Unit] =
    ZLayer.suspend {
      sharedLoomExecutor.fold(
        ZLayer.fail(_),
        Runtime.setExecutor
      )
    }

  /**
   * A layer that makes an executor that runs each task on a new virtual
   * thread the blocking executor of the effects it is provided to, as
   * `Runtime.setBlockingExecutor` does (JVM only). The layer fails with a
   * `LoomSupport.LoomNotAvailableException` ("Loom API not available") on
   * Java versions before 21. It shares one executor instance with
   * `enableLoomBasedExecutor`.
   */
  def enableLoomBasedBlockingExecutor(implicit trace: Trace): ZLayer[Any, LoomNotAvailableException, Unit] =
    ZLayer.suspend {
      sharedLoomExecutor.fold(
        ZLayer.fail(_),
        Runtime.setBlockingExecutor
      )
    }

  // a single Loom executor instance that can be shared between blocking and non-blocking fibers
  private lazy val sharedLoomExecutor: Either[LoomNotAvailableException, Executor] =
    LoomSupport.newVirtualThreadPerTaskExecutor() match {
      case None           => Left(LoomNotAvailableException("Loom API not available", null))
      case Some(executor) => Right(Executor.fromJavaExecutor(executor))
    }

  /**
   * A layer that replaces the executor of the effects it is provided to with a
   * new ZIO scheduler that detects blocking (JVM and Scala Native only). A
   * background thread checks the worker threads about every 100 milliseconds
   * and replaces a worker that is stuck on one task; tasks submitted from code
   * locations that are often found stuck then run on the blocking executor.
   * Each build of the layer creates a new scheduler with its own threads.
   */
  def enableAutoBlockingExecutor(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.suspend {
      Runtime.setExecutor(Executor.makeDefault(autoBlocking = true))
    }
}
