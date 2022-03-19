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
sealed abstract class FiberRef[A] extends Serializable { self =>

  /**
   * Reads the value associated with the current fiber. Returns initial value if
   * no value was `set` or inherited from parent.
   */
  def get(implicit trace: ZTraceElement): UIO[A]

  /**
   * Returns the initial value or error.
   */
  def initialValue: A

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `acquireRelease`.
   */
  def locally[R, E, B](value: A)(use: ZIO[R, E, B])(implicit trace: ZTraceElement): ZIO[R, E, B]

  /**
   * Returns a scoped effect that sets the value associated with the curent
   * fiber to the specified value and restores it to its original value when the
   * scope is closed.
   */
  def locallyScoped(value: A)(implicit trace: ZTraceElement): ZIO[Scope, Nothing, Unit]

  /**
   * Sets the value associated with the current fiber.
   */
  def set(value: A)(implicit trace: ZTraceElement): UIO[Unit]

  /**
   * Gets the value associated with the current fiber and uses it to run the
   * specified effect.
   */
  def getWith[R, E, B](f: A => ZIO[R, E, B])(implicit trace: ZTraceElement): ZIO[R, E, B] =
    get.flatMap(f)
}

object FiberRef {

  lazy val currentLogLevel: FiberRef.Runtime[LogLevel] =
    FiberRef.unsafeMake(LogLevel.Info)

  lazy val currentLogSpan: FiberRef.Runtime[List[LogSpan]] =
    FiberRef.unsafeMake(Nil)

  lazy val currentLogAnnotations: FiberRef.Runtime[Map[String, String]] =
    FiberRef.unsafeMake(Map.empty)

  /**
   * Creates a new `FiberRef` with given initial value.
   */
  def make[A](
    initial: => A,
    fork: A => A = (a: A) => a,
    join: (A, A) => A = ((_: A, a: A) => a)
  )(implicit trace: ZTraceElement): UIO[FiberRef.Runtime[A]] =
    ZIO.suspendSucceed {
      val ref = unsafeMake(initial, fork, join)

      ref.update(identity(_)).as(ref)
    }

  private[zio] def unsafeMake[A](
    initial: A,
    fork: A => A = (a: A) => a,
    join: (A, A) => A = ((_: A, a: A) => a)
  ): FiberRef.Runtime[A] =
    new FiberRef.Runtime[A](initial, fork, join)

  final class Runtime[A] private[zio] (
    private[zio] val initial: A,
    private[zio] val fork: A => A,
    private[zio] val join: (A, A) => A
  ) extends FiberRef[A] { self =>
    type ValueType = A

    def delete(implicit trace: ZTraceElement): UIO[Unit] =
      new ZIO.FiberRefDelete(self, trace)

    def get(implicit trace: ZTraceElement): IO[Nothing, A] =
      modify(v => (v, v))

    def getAndSet(a: A)(implicit trace: ZTraceElement): UIO[A] =
      modify(v => (v, a))

    def getAndUpdate(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = f(v)
        (v, result)
      }

    def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (v, result)
      }

    override def getWith[R, E, B](f: A => ZIO[R, E, B])(implicit trace: ZTraceElement): ZIO[R, E, B] =
      new ZIO.FiberRefWith(self, f, trace)

    def initialValue: A = initial

    def locally[R, EC, C](value: A)(use: ZIO[R, EC, C])(implicit trace: ZTraceElement): ZIO[R, EC, C] =
      new ZIO.FiberRefLocally(value, self, use, trace)

    def locallyScoped(value: A)(implicit trace: ZTraceElement): ZIO[Scope, Nothing, Unit] =
      ZIO.acquireRelease(get.flatMap(old => set(value).as(old)))(set).unit

    def modify[B](f: A => (B, A))(implicit trace: ZTraceElement): UIO[B] =
      new ZIO.FiberRefModify(this, f, trace)

    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: ZTraceElement): UIO[B] =
      modify { v =>
        pf.applyOrElse[A, (B, A)](v, _ => (default, v))
      }

    def reset(implicit trace: ZTraceElement): UIO[Unit] = set(initial)

    def set(value: A)(implicit trace: ZTraceElement): IO[Nothing, Unit] =
      modify(_ => ((), value))

    def update(f: A => A)(implicit trace: ZTraceElement): UIO[Unit] =
      modify { v =>
        val result = f(v)
        ((), result)
      }

    def updateAndGet(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = f(v)
        (result, result)
      }

    def updateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[Unit] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        ((), result)
      }

    def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (result, result)
      }

    /**
     * Returns a `ThreadLocal` that can be used to interact with this `FiberRef`
     * from side effecting code.
     *
     * This feature is meant to be used for integration with side effecting
     * code, that needs to access fiber specific data, like MDC contexts and the
     * like. The returned `ThreadLocal` will be backed by this `FiberRef` on all
     * threads that are currently managed by ZIO, and behave like an ordinary
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
            val fiberRef     = self.asInstanceOf[FiberRef.Runtime[Any]]

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

  implicit final class UnifiedSyntax[A](private val self: FiberRef[A]) extends AnyVal {

    /**
     * Atomically sets the value associated with the current fiber and returns
     * the old value.
     */
    def getAndSet(a: A)(implicit trace: ZTraceElement): UIO[A] =
      modify(v => (v, a))

    /**
     * Atomically modifies the `FiberRef` with the specified function and
     * returns the old value.
     */
    def getAndUpdate(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = f(v)
        (v, result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function
     * and returns the old value. If the function is undefined on the current
     * value it doesn't change it.
     */
    def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (v, result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified function, which
     * computes a return value for the modification. This is a more powerful
     * version of `update`.
     */
    def modify[B](f: A => (B, A))(implicit trace: ZTraceElement): UIO[B] =
      self match {
        case runtime: Runtime[A] => runtime.modify(f)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function,
     * which computes a return value for the modification if the function is
     * defined in the current value otherwise it returns a default value. This
     * is a more powerful version of `updateSome`.
     */
    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: ZTraceElement): UIO[B] =
      modify { v =>
        pf.applyOrElse[A, (B, A)](v, _ => (default, v))
      }

    /**
     * Atomically modifies the `FiberRef` with the specified function.
     */
    def update(f: A => A)(implicit trace: ZTraceElement): UIO[Unit] =
      modify { v =>
        val result = f(v)
        ((), result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified function and
     * returns the result.
     */
    def updateAndGet(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = f(v)
        (result, result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function.
     * If the function is undefined on the current value it doesn't change it.
     */
    def updateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[Unit] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        ((), result)
      }

    /**
     * Atomically modifies the `FiberRef` with the specified partial function.
     * If the function is undefined on the current value it returns the old
     * value without changing it.
     */
    def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      modify { v =>
        val result = pf.applyOrElse[A, A](v, identity)
        (result, result)
      }
  }

  private[zio] val forkScopeOverride: FiberRef.Runtime[Option[FiberScope]] =
    FiberRef.unsafeMake(None, _ => None, (a, _) => a)

  private[zio] val currentExecutor: FiberRef.Runtime[Option[zio.Executor]] =
    FiberRef.unsafeMake(None, a => a, (a, _) => a)

  private[zio] val currentEnvironment: FiberRef.Runtime[ZEnvironment[Any]] =
    FiberRef.unsafeMake(ZEnvironment.empty, a => a, (a, _) => a)
}
