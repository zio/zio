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

import zio.internal.{DefaultExecutors, ExecutionMetrics}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent._
import scala.concurrent.ExecutionContext

/**
 * An executor is responsible for executing actions: the `Runnable`s that the
 * ZIO runtime submits to it, such as the steps of fibers. The default
 * executors start each action on a fresh stack frame.
 * `fromExecutionContext`, `fromJavaExecutor` and
 * `fromThreadPoolExecutor` pass actions to the wrapped executor unchanged, so
 * an action runs on the caller's stack when the wrapped executor runs it on
 * the calling thread, as `ExecutionContext.parasitic` does.
 */
abstract class Executor extends ExecutorPlatformSpecific { self =>
  private val interop = new Executor.Interop(this)

  /**
   * Current sampled execution metrics, if available. The default executors
   * and the executors made with `fromThreadPoolExecutor` return them;
   * `fromJavaExecutor` and `fromExecutionContext` executors return `None`.
   */
  def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics]

  /**
   * Submits `runnable` for execution. Returns `true` when the executor
   * accepted it and `false` when it rejected it.
   */
  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean

  /**
   * Views this `Executor` as a Scala `ExecutionContext`. Its `execute` throws a
   * `java.util.concurrent.RejectedExecutionException` when `submit` rejects
   * the runnable, and its `reportFailure` prints the stack trace. An executor
   * made with `fromExecutionContext` returns the original `ExecutionContext`.
   */
  def asExecutionContext: ExecutionContext = interop

  /**
   * Views this `Executor` as a Java `Executor`. Its `execute` throws a
   * `java.util.concurrent.RejectedExecutionException` when `submit` rejects
   * the runnable. An executor made with `fromJavaExecutor` returns the
   * original Java `Executor`.
   */
  def asJava: java.util.concurrent.Executor = interop

  /**
   * Submits an effect for execution and signals that the current fiber is ready
   * to yield.
   *
   * '''NOTE''': On the default executor of the JVM and Scala Native, a call
   * from one of its worker threads whose local queue is empty makes the
   * runnable the next task of that same thread, unless the global queue holds
   * a task, which then runs first. This leads to improved performance as we
   * avoid unnecessary parking/un-parking of threads. Other executors submit the
   * runnable as `submit` does.
   */
  def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
    submit(runnable)

  /**
   * Submits an effect for execution and signals that the current fiber is ready
   * to yield, or throws a `java.util.concurrent.RejectedExecutionException`
   * ("Unable to run ...") when the executor rejects it.
   *
   * @see
   *   [[submitAndYield]] for an explanation of the implementation in the
   *   default executor of the JVM and Scala Native.
   */
  final def submitAndYieldOrThrow(runnable: Runnable)(implicit unsafe: Unsafe): Unit =
    if (!submitAndYield(runnable)) throw new RejectedExecutionException(s"Unable to run ${runnable.toString()}")

  /**
   * Submits an effect for execution, or throws a
   * `java.util.concurrent.RejectedExecutionException` ("Unable to run ...")
   * when the executor rejects it.
   */
  final def submitOrThrow(runnable: Runnable)(implicit unsafe: Unsafe): Unit =
    if (!submit(runnable)) throw new RejectedExecutionException(s"Unable to run ${runnable.toString()}")

  private[zio] def stealWork(depth: Int): Boolean =
    false

  /**
   * Used to identify whether the current thread is part of this executor.
   * @note
   *   This method is only overridden in the ZScheduler
   */
  private[zio] def isCurrentThreadInExecutor: Boolean =
    false
}

/**
 * Constructors of [[Executor]] values: `fromExecutionContext`,
 * `fromJavaExecutor` and, on the JVM and Scala Native, `makeDefault` and
 * `fromThreadPoolExecutor`.
 */
object Executor extends DefaultExecutors with Serializable {
  private final class Interop(executor: Executor) extends java.util.concurrent.Executor with ExecutionContext {
    override def execute(command: Runnable): Unit =
      if (!executor.submit(command)(Unsafe)) throw new RejectedExecutionException("Rejected: " + command.toString)
    override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
    override def toString                              = s"Executor.Interop($executor)"
  }

  /**
   * Creates an `Executor` from a Java `Executor`. Its `submit` returns `false`
   * when `executor` throws a `java.util.concurrent.RejectedExecutionException`,
   * its `metrics` are `None` and its `asJava` returns `executor`.
   */
  def fromJavaExecutor(executor: java.util.concurrent.Executor): Executor =
    new Executor {
      override def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] = None

      override def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
        try {
          executor.execute(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }

      override def asJava = executor
    }

  /**
   * Creates an `Executor` from a Scala `ExecutionContext`. Its `submit` returns
   * `false` when `ec` throws a
   * `java.util.concurrent.RejectedExecutionException`, its `metrics` are
   * `None` and its `asExecutionContext` returns `ec`.
   */
  def fromExecutionContext(ec: ExecutionContext): Executor =
    new Executor {

      def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
        try {
          ec.execute(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }

      def metrics(implicit unsafe: Unsafe) = None

      override def asExecutionContext = ec
    }
}
