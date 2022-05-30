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
trait FiberRef[A] extends Serializable { self =>

  /**
   * The type of the value of the `FiberRef`.
   */
  type Value = A

  /**
   * The type of the patch that describes updates to the value of the
   * `FiberRef`. In the simple case this will just be a function that sets the
   * value of the `FiberRef`. In more complex cases this will describe an update
   * to a piece of a whole value, allowing updates to the value by different
   * fibers to be combined in a compositional way when those fibers are joined.
   */
  type Patch

  /**
   * The initial value of the `FiberRef`.
   */
  def initial: Value

  /**
   * Constructs a patch describing the updates to a value from an old value and
   * a new value.
   */
  def diff(oldValue: Value, newValue: Value): Patch

  /**
   * Combines two patches to produce a new patch that describes the updates of
   * the first patch and then the updates of the second patch. The combine
   * operation should be associative. In addition, if the combine operation is
   * commutative then joining multiple fibers concurrently will result in
   * deterministic `FiberRef` values.
   */
  def combine(first: Patch, second: Patch): Patch

  /**
   * Applies a patch to an old value to produce a new value that is equal to the
   * old value with the updates described by the patch.
   */
  def patch(patch: Patch)(oldValue: Value): Value

  /**
   * The initial patch that is applied to the value of the `FiberRef` when a new
   * fiber is forked.
   */
  def fork: Patch

  def delete(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _, _) =>
      fiberState.unsafeDeleteFiberRef(self)

      ZIO.unit
    }

  /**
   * Reads the value associated with the current fiber. Returns initial value if
   * no value was `set` or inherited from parent.
   */
  def get(implicit trace: Trace): UIO[A] =
    ZIO.unsafeStateful[Any, Nothing, A] { (fiberState, _, _) =>
      ZIO.succeedNow(fiberState.unsafeGetFiberRef(self))
    }

  /**
   * Atomically sets the value associated with the current fiber and returns the
   * old value.
   */
  def getAndSet(newValue: A)(implicit trace: Trace): UIO[A] =
    ZIO.unsafeStateful[Any, Nothing, A] { (fiberState, _, _) =>
      val oldValue = fiberState.unsafeGetFiberRef(self)

      fiberState.unsafeSetFiberRef(self, newValue)

      ZIO.succeedNow(oldValue)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified function and returns
   * the old value.
   */
  def getAndUpdate(f: A => A)(implicit trace: Trace): UIO[A] =
    modify { v =>
      val result = f(v)
      (v, result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function and
   * returns the old value. If the function is undefined on the current value it
   * doesn't change it.
   */
  def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[A] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      (v, result)
    }

  /**
   * Gets the value associated with the current fiber and uses it to run the
   * specified effect.
   */
  def getWith[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, B] =
    ZIO.unsafeStateful[R, E, B] { (fiberState, _, _) =>
      f(fiberState.unsafeGetFiberRef(self))
    }

  /**
   * Returns a `ZIO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `acquireRelease`.
   */
  def locally[R, E, B](newValue: A)(zio: ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, B] =
    ZIO.unsafeStateful[R, E, B] { (fiberState, _, _) =>
      val oldValue = fiberState.unsafeGetFiberRef(self)

      fiberState.unsafeSetFiberRef(self, newValue)

      zio.ensuring(ZIO.succeed(fiberState.unsafeSetFiberRef(self, oldValue)))
    }

  /**
   * Returns a `ZIO` that runs with `f` applied to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `acquireRelease`.
   */
  def locallyWith[R, E, B](f: A => A)(zio: ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, B] =
    getWith(a => locally(f(a))(zio))

  /**
   * Returns a scoped workflow that sets the value associated with the curent
   * fiber to the specified value and restores it to its original value when the
   * scope is closed.
   */
  def locallyScoped(value: A)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(get.flatMap(old => set(value).as(old)))(set).unit

  /**
   * Returns a scoped workflow that updates the value associated with the
   * current fiber using the specified function and restores it to its original
   * value when the scope is closed.
   */
  def locallyScopedWith(f: A => A)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    getWith(a => locallyScoped(f(a))(trace))

  /**
   * Atomically modifies the `FiberRef` with the specified function, which
   * computes a return value for the modification. This is a more powerful
   * version of `update`.
   */
  def modify[B](f: A => (B, A))(implicit trace: Trace): UIO[B] =
    ZIO.unsafeStateful[Any, Nothing, B] { (fiberState, _, _) =>
      val (b, a) = f(fiberState.unsafeGetFiberRef(self))

      fiberState.unsafeSetFiberRef(self, a)

      ZIO.succeedNow(b)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function,
   * which computes a return value for the modification if the function is
   * defined in the current value otherwise it returns a default value. This is
   * a more powerful version of `updateSome`.
   */
  def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: Trace): UIO[B] =
    modify { v =>
      pf.applyOrElse[A, (B, A)](v, _ => (default, v))
    }

  def reset(implicit trace: Trace): UIO[Unit] =
    set(initial)

  /**
   * Sets the value associated with the current fiber.
   */
  def set(value: A)(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _, _) =>
      fiberState.unsafeSetFiberRef(self, value)

      ZIO.unit
    }

  /**
   * Atomically modifies the `FiberRef` with the specified function.
   */
  def update(f: A => A)(implicit trace: Trace): UIO[Unit] =
    modify { v =>
      val result = f(v)
      ((), result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified function and returns
   * the result.
   */
  def updateAndGet(f: A => A)(implicit trace: Trace): UIO[A] =
    modify { v =>
      val result = f(v)
      (result, result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function. If
   * the function is undefined on the current value it doesn't change it.
   */
  def updateSome(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[Unit] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      ((), result)
    }

  /**
   * Atomically modifies the `FiberRef` with the specified partial function. If
   * the function is undefined on the current value it returns the old value
   * without changing it.
   */
  def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[A] =
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
   * that are currently managed by ZIO when this feature is enabled using
   * [[Runtime.enableCurrentFiber]], and behave like an ordinary `ThreadLocal`
   * on all other threads.
   */
  def unsafeAsThreadLocal(implicit trace: Trace): UIO[ThreadLocal[A]] =
    ZIO.succeed {
      new ThreadLocal[A] {
        override def get(): A = {
          val fiber = Fiber._currentFiber.get()

          if (fiber eq null) super.get()
          else fiber.unsafeGetFiberRefOrElse(self, super.get())
        }

        override def set(a: A): Unit = {
          val fiber    = Fiber._currentFiber.get()
          val fiberRef = self.asInstanceOf[FiberRef[Any]]

          if (fiber eq null) super.set(a)
          else fiber.unsafeSetFiberRef(fiberRef, a)
        }

        override def remove(): Unit = {
          val fiber    = Fiber._currentFiber.get()
          val fiberRef = self

          if (fiber eq null) super.remove()
          else fiber.unsafeDeleteFiberRef(fiberRef)
        }

        override def initialValue(): A = initial
      }
    }
}

object FiberRef {
  import Differ._

  /**
   * Wraps another `FiberRef` and delegates all operations to it. Extend this if
   * you need a `FiberRef` with some specific behavior overridden.
   */
  abstract class Proxy[A](val delegate: FiberRef[A]) extends FiberRef[A] {
    override def initial: A = delegate.initial

    override type Patch = delegate.Patch

    override def diff(oldValue: Value, newValue: Value): Patch = delegate.diff(oldValue, newValue)

    override def combine(first: Patch, second: Patch): Patch = delegate.combine(first, second)

    override def patch(patch: Patch)(oldValue: Value): Value = delegate.patch(patch)(oldValue)

    override def fork: Patch = delegate.fork
  }

  type WithPatch[Value0, Patch0] = FiberRef[Value0] { type Patch = Patch0 }

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
  )(implicit trace: Trace): ZIO[Scope, Nothing, FiberRef[A]] =
    makeWith(unsafeMake(initial, fork, join))

  /**
   * Creates a new `FiberRef` with specified initial value of the
   * `ZEnvironment`, using `ZEnvironment.Patch` to combine updates to the
   * `ZEnvironment` in a compositional way.
   */
  def makeEnvironment[A](initial: => ZEnvironment[A])(implicit
    trace: Trace
  ): ZIO[Scope, Nothing, FiberRef.WithPatch[ZEnvironment[A], ZEnvironment.Patch[A, A]]] =
    makeWith(unsafeMakeEnvironment(initial))

  /**
   * Creates a new `FiberRef` with the specified initial value, using the
   * specified patch type to combine updates to the value in a compositional
   * way.
   */
  def makePatch[Value, Patch](
    initial: Value,
    differ: Differ[Value, Patch],
    fork: Patch
  )(implicit trace: Trace): ZIO[Scope, Nothing, FiberRef.WithPatch[Value, Patch]] =
    makeWith(unsafeMakePatch(initial, differ, fork))

  def makeSet[A](initial: => Set[A])(implicit
    trace: Trace
  ): ZIO[Scope, Nothing, FiberRef.WithPatch[Set[A], SetPatch[A]]] =
    makeWith(unsafeMakeSet(initial))

  private[zio] def unsafeMake[A](
    initial: A,
    fork: A => A = ZIO.identityFn[A],
    join: (A, A) => A = ((_: A, a: A) => a)
  ): FiberRef.WithPatch[A, A => A] =
    unsafeMakePatch[A, A => A](
      initial,
      Differ.updateWith[A](join),
      fork
    )

  private[zio] def unsafeMakeEnvironment[A](
    initial: ZEnvironment[A]
  ): FiberRef.WithPatch[ZEnvironment[A], ZEnvironment.Patch[A, A]] =
    unsafeMakePatch[ZEnvironment[A], ZEnvironment.Patch[A, A]](
      initial,
      Differ.environment,
      ZEnvironment.Patch.empty
    )

  private[zio] def unsafeMakePatch[Value0, Patch0](
    initialValue0: Value0,
    differ: Differ[Value0, Patch0],
    fork0: Patch0
  ): FiberRef.WithPatch[Value0, Patch0] =
    new FiberRef[Value0] {
      type Patch = Patch0
      def combine(first: Patch, second: Patch): Patch =
        differ.combine(first, second)
      def diff(oldValue: Value, newValue: Value): Patch =
        differ.diff(oldValue, newValue)
      def fork: Patch =
        fork0
      def initial: Value =
        initialValue0
      def patch(patch: Patch)(oldValue: Value): Value =
        differ.patch(patch)(oldValue)
    }

  private[zio] def unsafeMakeSet[A](
    initial: Set[A]
  ): FiberRef.WithPatch[Set[A], SetPatch[A]] =
    unsafeMakePatch[Set[A], SetPatch[A]](
      initial,
      Differ.set,
      SetPatch.empty
    )

  private[zio] val forkScopeOverride: FiberRef[Option[FiberScope]] =
    FiberRef.unsafeMake(None, _ => None)

  private[zio] val overrideExecutor: FiberRef[Option[Executor]] =
    FiberRef.unsafeMake(None)

  private[zio] val currentEnvironment: FiberRef.WithPatch[ZEnvironment[Any], ZEnvironment.Patch[Any, Any]] =
    FiberRef.unsafeMakeEnvironment(ZEnvironment.empty)

  private[zio] val interruptedCause: FiberRef[Cause[Nothing]] =
    FiberRef.unsafeMake(Cause.empty, identity(_), (parent, _) => parent)

  private[zio] val currentBlockingExecutor: FiberRef[Executor] =
    FiberRef.unsafeMake(Runtime.defaultBlockingExecutor)

  private[zio] val currentExecutor: FiberRef[Executor] =
    FiberRef.unsafeMake(Runtime.defaultExecutor)

  private[zio] val currentFatal: FiberRef.WithPatch[Set[Class[_ <: Throwable]], SetPatch[Class[_ <: Throwable]]] =
    FiberRef.unsafeMakeSet(Runtime.defaultFatal)

  private[zio] val currentLoggers: FiberRef.WithPatch[Set[ZLogger[String, Any]], SetPatch[ZLogger[String, Any]]] =
    FiberRef.unsafeMakeSet(Runtime.defaultLoggers)

  private[zio] val currentReportFatal: FiberRef[Throwable => Nothing] =
    FiberRef.unsafeMake(Runtime.defaultReportFatal)

  private[zio] val currentRuntimeFlags: FiberRef.WithPatch[Set[RuntimeFlag], SetPatch[RuntimeFlag]] =
    FiberRef.unsafeMakeSet(Runtime.defaultFlags)

  private[zio] val currentSupervisors: FiberRef.WithPatch[Set[Supervisor[Any]], SetPatch[Supervisor[Any]]] =
    FiberRef.unsafeMakeSet(Runtime.defaultSupervisors)

  private[zio] val interruptors: FiberRef[Set[ZIO[Any, Nothing, Nothing] => Unit]] = 
    FiberRef.unsafeMake(Set.empty, identity(_), (parent, _) => parent)

  private def makeWith[Value, Patch](
    ref: => FiberRef.WithPatch[Value, Patch]
  )(implicit trace: Trace): ZIO[Scope, Nothing, FiberRef.WithPatch[Value, Patch]] =
    ZIO.acquireRelease(ZIO.succeed(ref).tap(_.update(identity)))(_.delete)
}
