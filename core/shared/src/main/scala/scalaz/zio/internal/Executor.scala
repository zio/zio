// Copyright (C) 2018 - 2019 John A. De Goes. All rights reserved.
package scalaz.zio.internal

import java.util.concurrent._
import scala.concurrent.ExecutionContext

/**
 * An executor is responsible for executing actions. Each action is guaranteed
 * to begin execution on a fresh stack frame.
 */
trait Executor {

  /**
   * The role the executor is optimized for.
   */
  def role: Executor.Role

  /**
   * The number of operations a fiber should run before yielding.
   */
  def yieldOpCount: Int

  /**
   * Current sampled execution metrics, if available.
   */
  def metrics: Option[ExecutionMetrics]

  /**
   * Submits a task for execution.
   */
  def submit(runnable: Runnable): Boolean

  /**
   * Submits a task for execution or throws.
   */
  final def submitOrThrow(runnable: Runnable): Unit =
    if (!submit(runnable)) throw new RejectedExecutionException(s"Unable to run ${runnable.toString()}")

  /**
   * Whether or not the caller is being run on this executor.
   */
  def here: Boolean

  /**
   * Initiates shutdown of the executor.
   */
  def shutdown(): Unit

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

}

object Executor extends Serializable {
  sealed abstract class Role extends Product with Serializable

  /**
   * An executor optimized for synchronous tasks, which yield
   * to the runtime infrequently or never.
   */
  final case object Unyielding extends Role

  /**
   * An executor optimized for asynchronous tasks, which yield
   * frequently to the runtime.
   */
  final case object Yielding extends Role

  /**
   * Creates an `Executor` from a Scala `ExecutionContext`.
   */
  final def fromExecutionContext(role0: Role, yieldOpCount0: Int)(
    ec: ExecutionContext
  ): Executor =
    new Executor {
      def role = role0

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

      def shutdown(): Unit = ()
    }
}
