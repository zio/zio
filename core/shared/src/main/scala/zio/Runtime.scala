/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

import zio.internal.{FiberScope, Platform, FiberRuntime, StackBool, StackTraceBuilder}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.Future
import java.lang.ref.WeakReference

/**
 * A `Runtime[R]` is capable of executing tasks within an environment `R`.
 */
trait Runtime[+R] { self =>

  /**
   * The environment of the runtime.
   */
  def environment: ZEnvironment[R]

  /**
   * The `FiberRef` values that will be used for workflows executed by the
   * runtime.
   */
  def fiberRefs: FiberRefs =
    FiberRefs.empty

  def runtimeFlags: RuntimeFlags =
    RuntimeFlags.default

  /**
   * Constructs a new `Runtime` with the specified new environment.
   */
  def as[R1](r1: ZEnvironment[R1]): Runtime[R1] =
    map(_ => r1)

  /**
   * Constructs a new `Runtime` by mapping the environment.
   */
  def map[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime[R1] =
    Runtime(f(environment), fiberRefs)

  /**
   * Runs the effect "purely" through an async boundary. Useful for testing.
   */
  final def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.fiberId.flatMap { fiberId =>
      ZIO.asyncInterrupt[Any, E, A] { callback =>
        val canceler = unsafeRunAsyncCancelable(zio)(exit => callback(ZIO.done(exit)))
        Left(ZIO.succeedBlocking(canceler(fiberId)))
      }
    }

  /**
   * Executes the effect synchronously, failing with [[zio.FiberFailure]] if
   * there are any errors. May fail on Scala.js if the effect cannot be entirely
   * run synchronously.
   *
   * This method is effectful and should only be done at the edges of your
   * program.
   */
  final def unsafeRun[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): A =
    unsafeRunSync(zio).getOrElse(c => throw FiberFailure(c))

  /**
   * Executes the effect asynchronously, discarding the result of execution.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunAsync[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): Unit =
    unsafeRunAsyncWith(zio)(_ => ())

  /**
   * Executes the effect asynchronously, eventually passing the exit value to
   * the specified callback. It returns a callback, which can be used to
   * interrupt the running execution.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunSync[E, A](zio0: ZIO[R, E, A])(implicit trace: Trace): Exit[E, A] =
    defaultUnsafeRunSync(zio0)

  private[zio] final def defaultUnsafeRunSync[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): Exit[E, A] = {
    val result = internal.OneShot.make[Exit[E, A]]

    unsafeRunWith(zio)(result.set)

    result.get()
  }

  /**
   * Executes the effect asynchronously, eventually passing the exit value to
   * the specified callback.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunAsyncWith[E, A](
    zio: ZIO[R, E, A]
  )(k: Exit[E, A] => Any)(implicit trace: Trace): Unit = {
    unsafeRunAsyncCancelable(zio)(k)
    ()
  }

  /**
   * Executes the effect asynchronously, eventually passing the exit value to
   * the specified callback. It returns a callback, which can be used to
   * interrupt the running execution.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunAsyncCancelable[E, A](
    zio: ZIO[R, E, A]
  )(k: Exit[E, A] => Any)(implicit trace: Trace): FiberId => Exit[E, A] = {
    lazy val curZio = zio
    val canceler    = unsafeRunWith(curZio)(k)
    fiberId => {
      val result = internal.OneShot.make[Exit[E, A]]
      canceler(fiberId)(result.set)
      result.get()
    }
  }

  /**
   * Executes the Task/RIO effect synchronously, failing with the original
   * `Throwable` on both [[Cause.Fail]] and [[Cause.Die]]. In addition, appends
   * a new element to the `Throwable`s "caused by" chain, with this `Cause`
   * "pretty printed" (in stackless mode) as the message. May fail on Scala.js
   * if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be done at the edges of your
   * program.
   */
  final def unsafeRunTask[A](task: RIO[R, A])(implicit trace: Trace): A =
    unsafeRunSync(task).fold(cause => throw cause.squashTrace, identity)

  /**
   * Runs the IO, returning a Future that will be completed when the effect has
   * been executed.
   *
   * This method is effectful and should only be used at the edges of your
   * program.
   */
  final def unsafeRunToFuture[E <: Throwable, A](
    zio: ZIO[R, E, A]
  )(implicit trace: Trace): CancelableFuture[A] = {
    val p: scala.concurrent.Promise[A] = scala.concurrent.Promise[A]()

    val canceler = unsafeRunWith(zio)(_.fold(cause => p.failure(cause.squashTraceWith(identity)), p.success))

    new CancelableFuture[A](p.future) {
      def cancel(): Future[Exit[Throwable, A]] = {
        val p: scala.concurrent.Promise[Exit[Throwable, A]] = scala.concurrent.Promise[Exit[Throwable, A]]()
        canceler(FiberId.None)(p.success)
        p.future
      }
    }
  }

  private final def unsafeRunWith[E, A](
    zio: ZIO[R, E, A]
  )(k: Exit[E, A] => Any)(implicit trace: Trace): FiberId => (Exit[E, A] => Any) => Unit = {
    val canceler = unsafeRunWithRefs(zio, fiberRefs)((exit, _) => k(exit))
    fiberId => k => canceler(fiberId)((exit, _) => k(exit))
  }

  private final def unsafeRunWithRefs[E, A](
    zio: ZIO[R, E, A],
    fiberRefs0: FiberRefs
  )(
    k: (Exit[E, A], FiberRefs) => Any
  )(implicit trace: Trace): FiberId => ((Exit[E, A], FiberRefs) => Any) => Unit = {
    import internal.FiberRuntime

    val fiberId   = FiberId.unsafeMake(trace)
    val fiberRefs = fiberRefs0.updatedAs(fiberId)(FiberRef.currentEnvironment, environment)
    val fiber     = FiberRuntime[E, A](fiberId, fiberRefs, runtimeFlags)

    FiberScope.global.unsafeAdd(runtimeFlags, fiber)

    fiber.unsafeForeachSupervisor { supervisor =>
      if (supervisor != Supervisor.none) {
        supervisor.unsafeOnStart(environment, zio, None, fiber)

        fiber.unsafeAddObserver(exit => supervisor.unsafeOnEnd(exit, fiber))
      }
    }

    fiber.unsafeAddObserver { exit =>
      k(exit, fiber.unsafeGetFiberRefs())
    }

    fiber.start[R](zio)

    fiberId =>
      k =>
        unsafeRunWithRefs(fiber.interruptAs(fiberId), fiberRefs)(
          (exit: Exit[Nothing, Exit[E, A]], fiberRefs: FiberRefs) => k(exit.flatten, fiberRefs)
        )
  }
}

object Runtime extends RuntimePlatformSpecific {

  def addFatal(fatal: Class[_ <: Throwable])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentFatal.locallyScopedWith(_ + fatal))

  def addLogger(logger: ZLogger[String, Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentLoggers.locallyScopedWith(_ + logger))

  def addSupervisor(supervisor: Supervisor[Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentSupervisors.locallyScopedWith(_ + supervisor))

  /**
   * Builds a new runtime given an environment `R` and a [[zio.FiberRefs]].
   */
  def apply[R](
    r: ZEnvironment[R],
    fiberRefs0: FiberRefs,
    runtimeFlags0: RuntimeFlags = RuntimeFlags.default
  ): Runtime[R] =
    new Runtime[R] {
      val environment           = r
      override val fiberRefs    = fiberRefs0
      override val runtimeFlags = runtimeFlags0
    }

  /**
   * The default [[Runtime]] for most ZIO applications. This runtime is
   * configured with the the default runtime configuration, which is optimized
   * for typical ZIO applications.
   */
  val default: Runtime[Any] =
    Runtime(ZEnvironment.empty, FiberRefs.empty)

  def enableCurrentFiber(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.CurrentFiber))
    }

  def enableFiberRoots(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.FiberRoots))
    }

  def enableOpLog(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.OpLog))
    }

  val removeDefaultLoggers: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentLoggers.locallyScopedWith(_ -- Runtime.defaultLoggers))
  }

  def setBlockingExecutor(executor: Executor)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentBlockingExecutor.locallyScoped(executor))

  def setExecutor(executor: Executor)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentExecutor.locallyScoped(executor))

  def setReportFatal(reportFatal: Throwable => Nothing)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentReportFatal.locallyScoped(reportFatal))

  def enableOpSupervision(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
    }

  /**
   * A layer that adds a supervisor that tracks all forked fibers in a set. Note
   * that this may have a negative impact on performance.
   */
  def track(weak: Boolean)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    addSupervisor(Supervisor.unsafeTrack(weak))

  def enableRuntimeMetrics(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.RuntimeMetrics))
    }

  /**
   * Unsafely creates a `Runtime` from a `ZLayer` whose resources will be
   * allocated immediately, and not released until the `Runtime` is shut down or
   * the end of the application.
   *
   * This method is useful for small applications and integrating ZIO with
   * legacy code, but other applications should investigate using
   * [[ZIO.provide]] directly in their application entry points.
   */
  def unsafeFromLayer[R](layer: Layer[Any, R])(implicit trace: Trace): Runtime.Scoped[R] = {
    val (runtime, shutdown) = default.unsafeRun {
      Scope.make.flatMap { scope =>
        scope.extend(layer.toRuntime).flatMap { acquire =>
          val finalizer = () =>
            default.unsafeRun {
              scope.close(Exit.unit).uninterruptible.unit
            }

          ZIO.succeed(Platform.addShutdownHook(finalizer)).as((acquire, finalizer))
        }
      }
    }

    Runtime.Scoped(runtime.environment, runtime.fiberRefs, () => shutdown())
  }

  class Proxy[+R](underlying: Runtime[R]) extends Runtime[R] {
    def environment        = underlying.environment
    override def fiberRefs = underlying.fiberRefs
  }

  /**
   * A runtime that can be shutdown to release resources allocated to it.
   */
  abstract class Scoped[+R] extends Runtime[R] {

    /**
     * Shuts down this runtime and releases resources allocated to it. Once this
     * runtime has been shut down the behavior of methods on it is undefined and
     * it should be discarded.
     */
    def shutdown(): Unit

    override final def as[R1](r1: ZEnvironment[R1]): Runtime.Scoped[R1] =
      map(_ => r1)

    override final def map[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime.Scoped[R1] =
      Scoped(f(environment), fiberRefs, () => shutdown())
  }

  object Scoped {

    /**
     * Builds a new scoped runtime given an environment `R`, a
     * [[zio.FiberRefs]], and a shut down action.
     */
    def apply[R](
      r: ZEnvironment[R],
      fiberRefs0: FiberRefs = FiberRefs.empty,
      shutdown0: () => Unit
    ): Runtime.Scoped[R] =
      new Runtime.Scoped[R] {
        val environment        = r
        def shutdown()         = shutdown0()
        override val fiberRefs = fiberRefs0
      }
  }
}
