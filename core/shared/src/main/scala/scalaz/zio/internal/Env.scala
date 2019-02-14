// Copyright (C) 2018 - 2019 John A. De Goes. All rights reserved.
package scalaz.zio.internal

import java.util

import scalaz.zio._
import scalaz.zio.Exit.Cause
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
   * Determines if a throwable is non-fatal or not.
   */
  def nonFatal(t: Throwable): Boolean

  /**
   * Reports the specified failure.
   */
  def reportFailure(cause: Cause[_]): UIO[_]

  /**
   * Create a new java.util.WeakHashMap if supported by the env, otherwise any implementation of Map.
   */
  def newWeakHashMap[A, B](): util.Map[A, B]

  /**
   * Awaits for the result of the fiber to be computed.
   */
  final def unsafeRun[E, A](io: IO[E, A]): A =
    unsafeRunSync(io).getOrElse(c => throw new FiberFailure(c))

  /**
   * Awaits for the result of the fiber to be computed.
   */
  final def unsafeRunSync[E, A](io: IO[E, A]): Exit[E, A] = {
    val result = OneShot.make[Exit[E, A]]

    unsafeRunAsync(io, (x: Exit[E, A]) => result.set(x))

    result.get()
  }

  /**
   * Runs the `io` asynchronously.
   */
  final def unsafeRunAsync[E, A](
    io: IO[E, A],
    k: Exit[E, A] => Unit
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
   * Shuts down executors. You can try calling others method after this
   * one, but I predict you're going to be disappointed.
   */
  final def shutdown(): Unit = {
    executor(Executor.Yielding).shutdown()
    executor(Executor.Unyielding).shutdown()
  }

  /**
   * Helper function to create a new fiber context.
   */
  private[internal] final def newFiberContext[E, A](unhandled: Cause[Any] => UIO[_]): FiberContext[E, A] =
    new FiberContext[E, A](this, FiberCounter.fiberCounter.getAndIncrement(), unhandled)
}

private[zio] object FiberCounter {

  /**
   * The global counter for assigning fiber identities on creation.
   */
  val fiberCounter = new AtomicLong(0)
}
