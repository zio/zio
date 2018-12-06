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
   * Retrieves the executor for the specified type of tasks.
   */
  def executor(tpe: Executor.Type): Executor

  /**
   * Retrieves the scheduler.
   */
  def scheduler: Scheduler

  /**
   * Determines if a throwable is non-fatal or not.
   */
  def nonFatal(t: Throwable): Boolean

  /**
   * Awaits for the result of the fiber to be computed.
   */
  final def unsafeRun[E, A](unhandled: Cause[Any] => IO[Nothing, _], io: IO[E, A]): A = {
    val exit = unsafeRunSync(unhandled, io)

    exit.fold(cause => throw new FiberFailure(cause), identity)
  }

  /**
   * Awaits for the result of the fiber to be computed.
   */
  final def unsafeRunSync[E, A](unhandled: Cause[Any] => IO[Nothing, _], io: IO[E, A]): ExitResult[E, A] = {
    val result = OneShot.make[ExitResult[E, A]]

    unsafeRunAsync(unhandled, io, (x: ExitResult[E, A]) => result.set(x))

    result.get
  }

  /**
   */
  final def unsafeRunAsync[E, A](
    unhandled: Cause[Any] => IO[Nothing, _],
    io: IO[E, A],
    k: ExitResult[E, A] => Unit
  ): Unit = {
    val context = newFiberContext[E, A](unhandled)

    context.evaluate(io)
    context.runAsync(k)
  }

  /**
   * Helper function to create a new fiber context.
   */
  private[internal] final def newFiberContext[E, A](unhandled: Cause[Any] => IO[Nothing, _]): FiberContext[E, A] =
    new FiberContext[E, A](1024, this, Env.fiberCounter.getAndIncrement(), unhandled)
}

object Env {

  /**
   * The global counter for assigning fiber identities on creation.
   */
  private val fiberCounter = new AtomicLong(0)

  /**
   * Creates a new default environment.
   */
  final def newDefaultEnv(): Env =
    new Env {
      val sync  = Executor.newDefaultExecutor(Executor.Type.Synchronous)
      val async = Executor.newDefaultExecutor(Executor.Type.Asynchronous)

      def executor(tpe: Executor.Type): Executor = tpe match {
        case Executor.Type.Synchronous  => sync
        case Executor.Type.Asynchronous => async
      }

      val scheduler = Scheduler.newDefaultScheduler()

      def nonFatal(t: Throwable): Boolean =
        !t.isInstanceOf[VirtualMachineError]
    }
}
