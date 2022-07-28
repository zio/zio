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

import zio.internal.{FiberRuntime, FiberScope, IsFatal, Platform, StackTraceBuilder}
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
   * The [[zio.FiberRefs]] that will be used for all effects executed by this
   * runtime.
   */
  def fiberRefs: FiberRefs

  /**
   * Constructs a new `Runtime` by mapping the environment.
   */
  def mapEnvironment[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime[R1] =
    Runtime(f(environment), fiberRefs, runtimeFlags)

  /**
   * Runs the effect "purely" through an async boundary. Useful for testing.
   */
  final def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.fiberId.flatMap { fiberId =>
      ZIO.asyncInterrupt[Any, E, A] { callback =>
        val fiber = unsafe.fork(zio)(trace, Unsafe.unsafe)
        fiber.unsafe.addObserver(exit => callback(ZIO.done(exit)))(Unsafe.unsafe)
        Left(ZIO.blocking(fiber.interruptAs(fiberId)))
      }
    }

  /**
   * Access the loggers provided by application
   */
  def providedLoggers: Set[ZLogger[String, Any]] =
    fiberRefs.get(FiberRef.currentLoggers).map(_ -- Runtime.defaultLoggers).getOrElse(Set.empty)

  /**
   * The [[zio.RuntimeFlags]] that will be used for all effects executed by this
   * [[zio.Runtime]].
   */
  def runtimeFlags: RuntimeFlags

  /**
   * Constructs a new `Runtime` with the specified new environment.
   */
  def withEnvironment[R1](r1: ZEnvironment[R1]): Runtime[R1] =
    mapEnvironment(_ => r1)

  trait UnsafeAPI {

    /**
     * Executes the effect synchronously and returns its result as a
     * [[zio.Exit]] value. May fail on Scala.js if the effect cannot be entirely
     * run synchronously.
     *
     * This method is effectful and should only be used at the edges of your
     * application.
     */
    def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): Exit[E, A]

    /**
     * Executes the effect asynchronously, returning a fiber whose methods can
     * await the exit value of the fiber or interrupt the fiber.
     *
     * This method is effectful and should only be used at the edges of your
     * application.
     */
    def fork[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): Fiber.Runtime[E, A]

    /**
     * Executes the effect asynchronously, returning a Future that will be
     * completed when the effect has been fully executed. The Future can be
     * canceled, which will be translated into ZIO's interruption model.
     *
     * This method is effectful and should only be used at the edges of your
     * application.
     */
    def runToFuture[E <: Throwable, A](
      zio: ZIO[R, E, A]
    )(implicit trace: Trace, unsafe: Unsafe): CancelableFuture[A]
  }

  def unsafe: UnsafeAPI =
    new UnsafeAPIV1 {}

  protected abstract class UnsafeAPIV1 extends UnsafeAPI {
    def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): Exit[E, A] = {
      import internal.FiberRuntime

      val fiberId   = FiberId.make(trace)
      val fiberRefs = self.fiberRefs.updatedAs(fiberId)(FiberRef.currentEnvironment, environment)
      val fiber     = FiberRuntime[E, A](fiberId, fiberRefs, runtimeFlags)

      val supervisor = fiber.getSupervisor()

      if (supervisor != Supervisor.none) {
        supervisor.onStart(environment, zio, None, fiber)

        fiber.addObserver(exit => supervisor.onEnd(exit, fiber))
      }

      val fastExit = fiber.start[R](zio)

      if (fastExit != null) fastExit
      else {
        import internal.{FiberMessage, OneShot}
        FiberScope.global.add(runtimeFlags, fiber)
        val result = OneShot.make[Exit[E, A]]
        fiber.tell(
          FiberMessage.Stateful((fiber, _) => fiber.addObserver(exit => result.set(exit.asInstanceOf[Exit[E, A]])))
        )
        result.get()
      }
    }

    def fork[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): internal.FiberRuntime[E, A] = {
      import internal.FiberRuntime

      val fiberId   = FiberId.make(trace)
      val fiberRefs = self.fiberRefs.updatedAs(fiberId)(FiberRef.currentEnvironment, environment)
      val fiber     = FiberRuntime[E, A](fiberId, fiberRefs, runtimeFlags)

      FiberScope.global.add(runtimeFlags, fiber)

      val supervisor = fiber.getSupervisor()

      if (supervisor ne Supervisor.none) {
        supervisor.onStart(environment, zio, None, fiber)

        fiber.addObserver(exit => supervisor.onEnd(exit, fiber))
      }

      fiber.start[R](zio)
      fiber
    }

    def runToFuture[E <: Throwable, A](
      zio: ZIO[R, E, A]
    )(implicit trace: Trace, unsafe: Unsafe): CancelableFuture[A] = {
      val p: scala.concurrent.Promise[A] = scala.concurrent.Promise[A]()

      val fiber = fork(zio)

      fiber.addObserver(_.foldExit(cause => p.failure(cause.squashTraceWith(identity)), p.success))

      new CancelableFuture[A](p.future) {
        def cancel(): Future[Exit[Throwable, A]] = {
          val p: scala.concurrent.Promise[Exit[Throwable, A]] = scala.concurrent.Promise[Exit[Throwable, A]]()
          val cancelFiber                                     = fork(fiber.interruptAs(FiberId.None))
          cancelFiber.addObserver(_.foldExit(cause => p.failure(cause.squashTraceWith(identity)), p.success))
          p.future
        }
      }
    }
  }
}

object Runtime extends RuntimePlatformSpecific {

  def addFatal(fatal: Class[_ <: Throwable])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentFatal.locallyScopedWith(_ | IsFatal(fatal)))

  def addLogger(logger: ZLogger[String, Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentLoggers.locallyScopedWith(_ + logger))

  def addSupervisor(supervisor: Supervisor[Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentSupervisor.locallyScopedWith(_ ++ supervisor))

  /**
   * Builds a new runtime given an environment `R` and a [[zio.FiberRefs]].
   */
  def apply[R](
    r: ZEnvironment[R],
    fiberRefs0: FiberRefs,
    runtimeFlags0: RuntimeFlags
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
    Runtime(ZEnvironment.empty, FiberRefs.empty, RuntimeFlags.default)

  def enableCooperativeYielding(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.CooperativeYielding))
    }

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
    ZLayer.scoped(FiberRef.overrideExecutor.locallyScoped(Some(executor)))

  def setReportFatal(reportFatal: Throwable => Nothing)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentReportFatal.locallyScoped(reportFatal))

  def enableOpSupervision(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
    }

  def enableRuntimeMetrics(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(RuntimeFlag.RuntimeMetrics))
    }

  object unsafe {

    /**
     * Unsafely creates a `Runtime` from a `ZLayer` whose resources will be
     * allocated immediately, and not released until the `Runtime` is shut down
     * or the end of the application.
     *
     * This method is useful for small applications and integrating ZIO with
     * legacy code, but other applications should investigate using
     * [[ZIO.provide]] directly in their application entry points.
     */
    def fromLayer[R](layer: Layer[Any, R])(implicit trace: Trace, unsafe: Unsafe): Runtime.Scoped[R] = {
      val (runtime, shutdown) = default.unsafe.run {
        Scope.make.flatMap { scope =>
          scope.extend(layer.toRuntime).flatMap { acquire =>
            val finalizer = () =>
              default.unsafe.run {
                scope.close(Exit.unit).uninterruptible.unit
              }.getOrThrowFiberFailure()

            ZIO.succeed(Platform.addShutdownHook(finalizer)).as((acquire, finalizer))
          }
        }
      }.getOrThrowFiberFailure()

      Runtime.Scoped(runtime.environment, runtime.fiberRefs, runtime.runtimeFlags, () => shutdown())
    }
  }

  class Proxy[+R](underlying: Runtime[R]) extends Runtime[R] {
    def environment = underlying.environment
    def fiberRefs   = underlying.fiberRefs

    def runtimeFlags = underlying.runtimeFlags
  }

  /**
   * A runtime that can be shutdown to release resources allocated to it.
   */
  final case class Scoped[+R](
    environment: ZEnvironment[R],
    fiberRefs: FiberRefs,
    runtimeFlags: RuntimeFlags,
    shutdown0: () => Unit
  ) extends Runtime[R] { self =>
    override final def mapEnvironment[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime.Scoped[R1] =
      Scoped(f(environment), fiberRefs, runtimeFlags, shutdown0)

    trait UnsafeAPI2 {

      /**
       * Shuts down this runtime and releases resources allocated to it. Once
       * this runtime has been shut down the behavior of methods on it is
       * undefined and it should be discarded.
       */
      def shutdown()(implicit unsafe: Unsafe): Unit
    }

    override def unsafe: UnsafeAPI with UnsafeAPI2 =
      new UnsafeAPIV2 {}

    protected abstract class UnsafeAPIV2 extends UnsafeAPIV1 with UnsafeAPI2 {
      def shutdown()(implicit unsafe: Unsafe) = shutdown0()
    }

  }
}
