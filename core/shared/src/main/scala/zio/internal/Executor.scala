/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.internal

import java.util.concurrent._
import java.{ util => ju }

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService

/**
 * An executor is responsible for executing actions. Each action is guaranteed
 * to begin execution on a fresh stack frame.
 */
trait Executor { self =>

  /**
   * The number of operations a fiber should run before yielding.
   */
  def yieldOpCount: Int

  /**
   * Current sampled execution metrics, if available.
   */
  def metrics: Option[ExecutionMetrics]

  /**
   * Submits an effect for execution.
   */
  def submit(runnable: Runnable): Boolean

  /**
   * Submits an effect for execution or throws.
   */
  final def submitOrThrow(runnable: Runnable): Unit =
    if (!submit(runnable)) throw new RejectedExecutionException(s"Unable to run ${runnable.toString()}")

  /**
   * Whether or not the caller is being run on this executor.
   */
  def here: Boolean

  /**
   * Views this `Executor` as a Scala `ExecutionContext`.
   */
  lazy val asEC: ExecutionContext =
    new ExecutionContext {
      override def execute(r: Runnable): Unit =
        if (!submit(r)) throw new RejectedExecutionException("Rejected: " + r.toString)

      override def reportFailure(cause: Throwable): Unit =
        cause.printStackTrace
    }

  /**
   * Views this `Executor` as a Scala `ExecutionContextExecutorService`.
   */
  lazy val asECES: ExecutionContextExecutorService =
    new AbstractExecutorService with ExecutionContextExecutorService {
      override val prepare: ExecutionContext                               = asEC
      override val isShutdown: Boolean                                     = false
      override val isTerminated: Boolean                                   = false
      override val shutdown: Unit                                          = ()
      override val shutdownNow: ju.List[Runnable]                          = ju.Collections.emptyList[Runnable]
      override def execute(runnable: Runnable): Unit                       = asEC execute runnable
      override def reportFailure(t: Throwable): Unit                       = asEC reportFailure t
      override def awaitTermination(length: Long, unit: TimeUnit): Boolean = false
    }

}

object Executor extends DefaultExecutors with Serializable {

  /**
   * Creates an `Executor` from a Scala `ExecutionContext`.
   */
  def fromExecutionContext(yieldOpCount0: Int)(
    ec: ExecutionContext
  ): Executor =
    new Executor {
      def yieldOpCount = yieldOpCount0

      def submit(runnable: Runnable): Boolean =
        try {
          ec.execute(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }

      def here = false

      def metrics = None
    }
}
