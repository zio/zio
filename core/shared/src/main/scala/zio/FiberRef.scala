/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import zio.internal.FiberScope
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `FiberRef` is ZIO's equivalent of Java's `ThreadLocal`. The value of a
 * `FiberRef` is automatically propagated to child fibers when they are forked
 * and merged back in to the value of the parent fiber after they are joined.
 *
 * {{{
 * for {
 *   fiberRef <- FiberRef.make("Hello world!")
 *   child    <- fiberRef.set("Hi!).fork
 *   result   <- child.join
 * } yield result
 * }}}
 *
 * Here `result` will be equal to "Hi!" since changed made by a child fiber are
 * merged back in to the value of the parent fiber on join.
 *
 * By default the value of the child fiber will replace the value of the parent
 * fiber on join but you can specify your own logic for how values should be
 * merged.
 *
 * {{{
 * for {
 *   fiberRef <- FiberRef.make(0, math.max)
 *   child    <- fiberRef.update(_ + 1).fork
 *   _        <- fiberRef.update(_ + 2)
 *   _        <- child.join
 *   value    <- fiberRef.get
 * } yield value
 * }}}
 *
 * Here `value` will be 2 as the value in the joined fiber is lower and we
 * specified `max` as our combining function.
 */
final class FiberRef[A] private[zio] (
  private[zio] val initial: A,
  private[zio] val fork: A => A,
  private[zio] val join: (A, A) => A
) extends Serializable { self =>
  type ValueType = A

  def delete(implicit trace: ZTraceElement): UIO[Unit] =
    new ZIO.FiberRefDelete(self, trace)

  /**
   * Reads the value associated with the current fiber. Returns initial value if
   * no value was `set` or inherited from parent.
   */
  def get(implicit trace: ZTraceElement): UIO[A] =
    modify(v => (v, v))

  /**
   * Atomically sets the value associated with the current fiber and returns the
   * old value.
   */
  def getAndSet(a: A)(implicit trace: ZTraceElement): UIO[A] =
    modify(v => (v, a))

  /**
   * Atomically modifies the `FiberRef` with the specified function and returns
   * the old value.
   */
  def getAndUpdate(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
    modify { v =>
      val result = f(v)
      (v, result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function and
   * returns the old value. If the function is undefined on the current value it
   * doesn't change it.
   */
  def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      (v, result)
    }

  /**
   * Gets the value associated with the current fiber and uses it to run the
   * specified effect.
   */
  def getWith[R, E, B](f: A => ZIO[R, E, B])(implicit trace: ZTraceElement): ZIO[R, E, B] =
    new ZIO.FiberRefWith(self, f, trace)

  /**
   * Returns the initial value or error.
   */
  def initialValue: A =
    initial

  /**
   * Returns a `ZIO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `acquireRelease`.
   */
  def locally[R, E, B](value: A)(zio: ZIO[R, E, B])(implicit trace: ZTraceElement): ZIO[R, E, B] =
    new ZIO.FiberRefLocally(value, self, zio, trace)

  /**
   * Returns a `ZIO` that runs with `f` applied to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `acquireRelease`.
   */
  def locallyWith[R, E, B](f: A => A)(zio: ZIO[R, E, B])(implicit trace: ZTraceElement): ZIO[R, E, B] =
    getWith(a => locally(f(a))(zio))

  /**
   * Returns a scoped workflow that sets the value associated with the curent
   * fiber to the specified value and restores it to its original value when the
   * scope is closed.
   */
  def locallyScoped(value: A)(implicit trace: ZTraceElement): ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(get.flatMap(old => set(value).as(old)))(set).unit

  /**
   * Returns a scoped workflow that updates the value associated with the
   * current fiber using the specified function and restores it to its original
   * value when the scope is closed.
   */
  def locallyScopedWith(f: A => A)(implicit trace: ZTraceElement): ZIO[Scope, Nothing, Unit] =
    getWith(a => locallyScoped(f(a))(trace))

  /**
   * Atomically modifies the `FiberRef` with the specified function, which
   * computes a return value for the modification. This is a more powerful
   * version of `update`.
   */
  def modify[B](f: A => (B, A))(implicit trace: ZTraceElement): UIO[B] =
    new ZIO.FiberRefModify(this, f, trace)

  /**
   * Atomically modifies the `FiberRef` with the specified partial function,
   * which computes a return value for the modification if the function is
   * defined in the current value otherwise it returns a default value. This is
   * a more powerful version of `updateSome`.
   */
  def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: ZTraceElement): UIO[B] =
    modify { v =>
      pf.applyOrElse[A, (B, A)](v, _ => (default, v))
    }

  def reset(implicit trace: ZTraceElement): UIO[Unit] =
    set(initial)

  /**
   * Sets the value associated with the current fiber.
   */
  def set(value: A)(implicit trace: ZTraceElement): UIO[Unit] =
    modify(_ => ((), value))

  /**
   * Atomically modifies the `FiberRef` with the specified function.
   */
  def update(f: A => A)(implicit trace: ZTraceElement): UIO[Unit] =
    modify { v =>
      val result = f(v)
      ((), result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified function and returns
   * the result.
   */
  def updateAndGet(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
    modify { v =>
      val result = f(v)
      (result, result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function. If
   * the function is undefined on the current value it doesn't change it.
   */
  def updateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[Unit] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      ((), result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function. If
   * the function is undefined on the current value it returns the old value
   * without changing it.
   */
  def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      (result, result)
    }

  /**
   * Returns a `ThreadLocal` that can be used to interact with this `FiberRef`
   * from side effecting code.
   *
   * This feature is meant to be used for integration with side effecting code,
   * that needs to access fiber specific data, like MDC contexts and the like.
   * The returned `ThreadLocal` will be backed by this `FiberRef` on all threads
   * that are currently managed by ZIO, and behave like an ordinary
   * `ThreadLocal` on all other threads.
   */
  def unsafeAsThreadLocal(implicit trace: ZTraceElement): UIO[ThreadLocal[A]] =
    ZIO.succeed {
      new ThreadLocal[A] {
        override def get(): A = {
          val fiberContext = Fiber._currentFiber.get()

          if (fiberContext eq null) super.get()
          else fiberContext.fiberRefLocals.get.getOrElse(self, super.get()).asInstanceOf[A]
        }

        override def set(a: A): Unit = {
          val fiberContext = Fiber._currentFiber.get()
          val fiberRef     = self.asInstanceOf[FiberRef[Any]]

          if (fiberContext eq null) super.set(a)
          else fiberContext.unsafeSetRef(fiberRef, a)
        }

        override def remove(): Unit = {
          val fiberContext = Fiber._currentFiber.get()
          val fiberRef     = self

          if (fiberContext eq null) super.remove()
          else fiberContext.unsafeDeleteRef(fiberRef)
        }

        override def initialValue(): A = initial
      }
    }
}

object FiberRef {

  lazy val currentLogLevel: FiberRef[LogLevel] =
    FiberRef.unsafeMake(LogLevel.Info)

  lazy val currentLogSpan: FiberRef[List[LogSpan]] =
    FiberRef.unsafeMake(Nil)

  lazy val currentLogAnnotations: FiberRef[Map[String, String]] =
    FiberRef.unsafeMake(Map.empty)

  /**
   * Creates a new `FiberRef` with given initial value.
   */
  def make[A](
    initial: => A,
    fork: A => A = (a: A) => a,
    join: (A, A) => A = ((_: A, a: A) => a)
  )(implicit trace: ZTraceElement): UIO[FiberRef[A]] =
    ZIO.suspendSucceed {
      val ref = unsafeMake(initial, fork, join)

      ref.update(identity(_)).as(ref)
    }

  private[zio] def unsafeMake[A](
    initial: A,
    fork: A => A = (a: A) => a,
    join: (A, A) => A = ((_: A, a: A) => a)
  ): FiberRef[A] =
    new FiberRef[A](initial, fork, join)

  private[zio] val forkScopeOverride: FiberRef[Option[FiberScope]] =
    FiberRef.unsafeMake(None, _ => None, (a, _) => a)

  private[zio] val currentExecutor: FiberRef[Option[zio.Executor]] =
    FiberRef.unsafeMake(None, a => a, (a, _) => a)

  private[zio] val currentEnvironment: FiberRef[ZEnvironment[Any]] =
    FiberRef.unsafeMake(ZEnvironment.empty, a => a, (a, _) => a)
}
