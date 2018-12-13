// Copyright (C) 2018 - 2019 John A. De Goes. All rights reserved.
package scalaz.zio.internal

import scalaz.zio._
import scalaz.zio.ExitResult.Cause
import java.util.concurrent.atomic.AtomicLong

/**
 * An environment provides the capability to execute different types
 * of tasks.
 */
trait Env {

  /**
   * Retrieves the default executor.
   */
  def defaultExecutor: Executor = executor(Executor.Yielding)

  /**
   * Retrieves the executor for the specified type of tasks.
   */
  def executor(tpe: Executor.Role): Executor

  /**
   * Retrieves the scheduler.
   */
  def scheduler: Scheduler

  /**
   * Determines if a throwable is non-fatal or not.
   */
  def nonFatal(t: Throwable): Boolean

  /**
   * Reports the specified failure.
   */
  def reportFailure(cause: Cause[_]): IO[Nothing, _]

  /**
   * Awaits for the result of the fiber to be computed.
   */
  final def unsafeRun[E, A](io: IO[E, A], timeout: Long = Long.MaxValue): A =
    unsafeRunSync(io, timeout).fold(cause => throw new FiberFailure(cause), identity)

  /**
   * Awaits for the result of the fiber to be computed.
   */
  final def unsafeRunSync[E, A](io: IO[E, A], timeout: Long = Long.MaxValue): ExitResult[E, A] = {
    val result = OneShot.make[ExitResult[E, A]]

    unsafeRunAsync(io, (x: ExitResult[E, A]) => result.set(x))

    result.get(timeout)
  }

  /**
   * Runs the `io` asynchronously.
   */
  final def unsafeRunAsync[E, A](
    io: IO[E, A],
    k: ExitResult[E, A] => Unit
  ): Unit = {
    val context = newFiberContext[E, A](reportFailure(_))

    context.evaluateNow(io)
    context.runAsync(k)
  }

  /**
   * Runs the `io` asynchronously, ignoring the results.
   */
  final def unsafeRunAsync_[E, A](io: IO[E, A]): Unit = {
    val context = newFiberContext[E, A](reportFailure(_))

    val _ = context.evaluateNow(io)
  }

  /**
   * Helper function to create a new fiber context.
   */
  private[internal] final def newFiberContext[E, A](unhandled: Cause[Any] => IO[Nothing, _]): FiberContext[E, A] =
    new FiberContext[E, A](this, Env.fiberCounter.getAndIncrement(), unhandled)
}

object Env {

  /**
   * The global counter for assigning fiber identities on creation.
   */
  private val fiberCounter = new AtomicLong(0)

  /**
   * Creates a new default environment.
   */
  final def newDefaultEnv(reportFailure0: Cause[_] => IO[Nothing, _]): Env =
    new Env {
      val sync  = Executor.newDefaultExecutor(Executor.Unyielding)
      val async = Executor.newDefaultExecutor(Executor.Yielding)

      def executor(tpe: Executor.Role): Executor = tpe match {
        case Executor.Unyielding => sync
        case Executor.Yielding   => async
      }

      val scheduler = Scheduler.newDefaultScheduler()

      def nonFatal(t: Throwable): Boolean =
        !t.isInstanceOf[VirtualMachineError]

      def reportFailure(cause: Cause[_]): IO[Nothing, _] =
        reportFailure0(cause)
    }
}
