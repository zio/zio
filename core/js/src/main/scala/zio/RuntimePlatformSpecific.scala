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

import org.scalajs.macrotaskexecutor.MacrotaskExecutor
import zio.internal.IsFatal
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.scalajs.js.Dynamic.{global => jsglobal}

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
    Executor.fromExecutionContext(MacrotaskExecutor)

  /**
   * The executor that runs blocking effects, such as `ZIO.attemptBlocking`, by
   * default. On the JVM and Scala Native it is a thread pool that starts a
   * daemon thread named `zio-default-blocking-N` whenever no idle thread is
   * free, with no upper limit, and stops threads that stay idle for 60 seconds.
   * On Scala.js, which has a single thread, it is `defaultExecutor`.
   */
  final val defaultBlockingExecutor: Executor =
    defaultExecutor

  /**
   * An `IsFatal` that matches `VirtualMachineError`s, such as
   * `StackOverflowError`, and no other error. Kept for binary compatibility:
   * which errors count as fatal is no longer configurable.
   */
  @deprecated("IsFatal is deprecated, kept only for binary compatability.", "2.1.23")
  final val defaultFatal: IsFatal =
    IsFatal.empty

  /**
   * The function that the runtime calls by default with a fatal error. On the
   * JVM it prints the stack trace, calls `java.lang.System.exit(-1)` and throws
   * the error if the program does not end. On Scala.js and Scala Native it
   * prints the stack trace and throws the error.
   */
  final val defaultReportFatal: Throwable => Nothing =
    (t: Throwable) => {
      t.printStackTrace()
      throw t
    }

  /**
   * The loggers of a new runtime: one logger that writes the entries of level
   * `Info` and above in the format of `ZLogger.default` (`timestamp=...
   * level=INFO thread=#zio-fiber-... message="..."`). On the JVM and Scala
   * Native it prints them to standard output. On Scala.js it writes them to the
   * JavaScript console, with `console.error` for `Error` and `Fatal` entries
   * and `console.log` for the others, and ignores exceptions thrown while
   * logging.
   */
  final val defaultLoggers: Set[ZLogger[String, Any]] = {
    val logger: ZLogger[String, Unit] =
      (
        trace: Trace,
        fiberId: FiberId,
        level: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
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

    Set(logger.filterLogLevel(_ >= LogLevel.Info))
  }

  /**
   * The supervisor of a new runtime: `Supervisor.none`.
   */
  final val defaultSupervisor: Supervisor[Any] =
    Supervisor.none
}
