/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.{FiberRuntime, FiberScope, Platform}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

/**
 * A `Runtime[R]` runs ZIO effects that need an environment `R`. It holds the
 * environment, the [[zio.FiberRefs]] that each fiber it starts begins with (the
 * executor, loggers, supervisor and other fiber-local settings live there; a
 * `FiberRef` that is missing has its initial value), and the
 * [[zio.RuntimeFlags]] of those fibers.
 *
 * A ZIO application built with `ZIOAppDefault` creates and uses its runtime for
 * you. Use a `Runtime` directly at the edge of code that is not written with
 * ZIO, through the methods of `unsafe`. `Runtime.default` is the runtime with
 * no environment and the default settings, `Runtime.unsafe.fromLayer` builds a
 * runtime from a layer, and the layers in the companion object
 * (`Runtime.removeDefaultLoggers`, `Runtime.setExecutor`, ...) change the
 * settings for the effects they are provided to.
 *
 * {{{
 * val runtime: Runtime[Any] = Runtime.default
 *
 * val total: Int =
 *   Unsafe.unsafe { implicit unsafe =>
 *     runtime.unsafe.run(ZIO.succeed(20).map(_ + 22)).getOrThrowFiberFailure()
 *   }
 * // 42
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/core/runtime/ Runtime]]
 */
@deprecatedInheritance("Use Runtime.apply", since = "2.1.18")
trait Runtime[+R] { self =>

  /**
   * The environment that the effects run by this runtime receive.
   */
  def environment: ZEnvironment[R]

  /**
   * The [[zio.FiberRefs]] that each fiber started by this runtime begins with.
   */
  def fiberRefs: FiberRefs

  /**
   * Constructs a new `Runtime` by mapping the environment.
   */
  def mapEnvironment[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime[R1] =
    Runtime(f(environment), fiberRefs, runtimeFlags)

  /**
   * Returns an effect that runs `zio` in a new fiber of this runtime, with this
   * runtime's environment, `FiberRefs` and flags instead of the caller's, and
   * completes with its result. Interrupting the returned effect interrupts that
   * fiber.
   */
  final def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.fiberIdWith { fiberId =>
      ZIO.asyncInterrupt[Any, E, A] { callback =>
        val fiber = unsafe.fork(zio)(trace, Unsafe.unsafe)
        fiber.unsafe.addObserver(callback(_))(Unsafe.unsafe)
        Left(fiber.interruptAs(fiberId))
      }
    }

  /**
   * The [[zio.RuntimeFlags]] that each fiber started by this runtime begins
   * with.
   */
  def runtimeFlags: RuntimeFlags

  /**
   * Constructs a new `Runtime` with the specified new environment.
   */
  def withEnvironment[R1](r1: ZEnvironment[R1]): Runtime[R1] =
    mapEnvironment(_ => r1)

  /**
   * The methods that start effects from code that is not itself a ZIO effect:
   * `fork`, `run` and `runToFuture`. Each needs an implicit `Unsafe`.
   */
  trait UnsafeAPI {

    /**
     * Executes the effect asynchronously on the runtime's executor, returning a
     * fiber whose methods can await the exit value of the fiber or interrupt
     * the fiber. The fiber has no parent fiber.
     *
     * This method is effectful and should only be used at the edges of your
     * application.
     */
    def fork[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): Fiber.Runtime[E, A]

    /**
     * Executes the effect and returns its result as a [[zio.Exit]] value,
     * blocking the calling thread until the effect completes. The effect starts
     * on the calling thread and moves to the runtime's executor when it
     * suspends or yields. When the calling thread is interrupted while it
     * waits, the fiber is interrupted, the call waits for the fiber to end and
     * then throws the `InterruptedException`.
     *
     * On Scala.js, which cannot block, it throws a `java.lang.Error` ("Cannot
     * block for result to be set in JavaScript") when the effect does not
     * complete synchronously.
     *
     * This method is effectful and should only be used at the edges of your
     * application.
     */
    def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): Exit[E, A]

    /**
     * Executes the effect asynchronously on the runtime's executor, returning a
     * `Future` that completes when the effect ends: with its value, or failed
     * with its typed error or its defect. When a typed error and a defect occur
     * together, it fails with the typed error. When the effect is interrupted,
     * or dies with a `java.lang.Error` such as an `AssertionError`, the
     * `Future` fails with a `java.util.concurrent.ExecutionException` that
     * wraps the `InterruptedException` or the error.
     *
     * `cancel()` on the returned `CancelableFuture` interrupts the fiber and
     * returns a `Future` of the fiber's `Exit`, which completes once the fiber
     * has ended.
     *
     * This method is effectful and should only be used at the edges of your
     * application.
     */
    def runToFuture[E <: Throwable, A](
      zio: ZIO[R, E, A]
    )(implicit trace: Trace, unsafe: Unsafe): CancelableFuture[A]
  }

  /**
   * Adds `runOrFork` to the unsafe methods of a runtime.
   */
  trait UnsafeAPI3 {

    /**
     * Attempts to execute the effect synchronously on the calling thread and
     * returns its result as a [[zio.Exit]] value in a `Right`. If the effect
     * suspends or yields before it ends, it continues on the runtime's executor
     * and its fiber is returned in a `Left`.
     *
     * This method is effectful and should only be used at the edges of your
     * application.
     */
    def runOrFork[E, A](
      zio: ZIO[R, E, A]
    )(implicit trace: Trace, unsafe: Unsafe): Either[Fiber.Runtime[E, A], Exit[E, A]]
  }

  /**
   * The unsafe methods of this runtime (`run`, `fork`, `runToFuture` and
   * `runOrFork`), which start effects from code that is not itself a ZIO
   * effect.
   */
  def unsafe: UnsafeAPI with UnsafeAPI3 =
    new UnsafeAPIV1 {}

  protected abstract class UnsafeAPIV1 extends UnsafeAPI with UnsafeAPI3 {

    private val fiberIdGen = self.fiberRefs.getOrDefault(FiberRef.currentFiberIdGenerator)

    def fork[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): internal.FiberRuntime[E, A] = {
      val fiber = makeFiber(zio)
      fiber.startConcurrently(zio)
      fiber
    }

    def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace, unsafe: Unsafe): Exit[E, A] =
      runOrFork(zio) match {
        case Left(fiber) =>
          import internal.OneShot
          val result = OneShot.make[Exit[E, A]]
          fiber.unsafe.addObserver(result.set)
          scala.concurrent.blocking {
            try {
              result.get()
            } catch {
              case t: InterruptedException =>
                fiber.tellInterrupt(Cause.interrupt(FiberId.None, StackTrace(FiberId.None, Chunk.single(trace))))
                result.get() // wait for interruption to finish
                throw t
            }
          }
        case Right(exit) => exit
      }

    def runOrFork[E, A](
      zio: ZIO[R, E, A]
    )(implicit trace: Trace, unsafe: Unsafe): Either[internal.FiberRuntime[E, A], Exit[E, A]] = {
      import internal.FiberRuntime

      val fiberId   = fiberIdGen.make(trace)
      val fiberRefs = self.fiberRefs.updatedAs(fiberId)(FiberRef.currentEnvironment, environment)
      val fiber     = FiberRuntime[E, A](fiberId, fiberRefs.forkAs(fiberId), runtimeFlags)

      val supervisor = fiber.getSupervisor()

      if (supervisor ne Supervisor.none) {
        supervisor.onStart(environment, zio, None, fiber)
      }

      val exit = fiber.start[R](zio)

      if (exit ne null) Right(exit)
      else {
        FiberScope.global.add(null, runtimeFlags, fiber)
        Left(fiber)
      }
    }

    def runToFuture[E <: Throwable, A](
      zio: ZIO[R, E, A]
    )(implicit trace: Trace, unsafe: Unsafe): CancelableFuture[A] = {
      val p: scala.concurrent.Promise[A] = scala.concurrent.Promise[A]()

      val fiber = makeFiber(zio)
      fiber.addObserver(_.foldExit(cause => p.failure(cause.squashTraceWith(identity)), p.success))
      fiber.startConcurrently(zio)

      new CancelableFuture[A](p.future) {
        def cancel(): Future[Exit[Throwable, A]] = {
          val p: scala.concurrent.Promise[Exit[Throwable, A]] = scala.concurrent.Promise[Exit[Throwable, A]]()
          fiber.unsafe.addObserver(p.success)
          fiber.tellInterrupt(Cause.interrupt(FiberId.None, StackTrace(FiberId.None, Chunk.single(trace))))
          p.future
        }
      }
    }

    private def makeFiber[E, A](
      zio: ZIO[R, E, A]
    )(implicit trace: Trace, unsafe: Unsafe): internal.FiberRuntime[E, A] = {
      val fiberId   = fiberIdGen.make(trace)
      val fiberRefs = self.fiberRefs.updatedAs(fiberId)(FiberRef.currentEnvironment, environment)
      val fiber     = FiberRuntime[E, A](fiberId, fiberRefs.forkAs(fiberId), runtimeFlags)

      FiberScope.global.add(null, runtimeFlags, fiber)

      val supervisor = fiber.getSupervisor()

      if (supervisor ne Supervisor.none) {
        supervisor.onStart(environment, zio, None, fiber)
      }

      fiber
    }
  }
}

/**
 * Constructors of [[Runtime]] values (`Runtime.default`, `Runtime.apply`,
 * `Runtime.unsafe.fromLayer`), the platform's default settings
 * (`defaultExecutor`, `defaultLoggers`, ...), and layers that change runtime
 * settings for the effects they are provided to: loggers, log annotations,
 * executors, runtime flags, supervisors, the config provider and the reporting
 * of fatal errors. A settings layer applies its change while its scope is open
 * and reverts it when the scope closes.
 */
object Runtime extends RuntimePlatformSpecific {
  private[zio] sealed abstract class Internal[+R](
    val environment: ZEnvironment[R],
    val fiberRefs: FiberRefs,
    val runtimeFlags: RuntimeFlags
  ) extends Runtime[R] {
    // Thread unsafe lazy initialization
    protected var unsafe0: UnsafeAPI with UnsafeAPI3 = null
    override def unsafe: UnsafeAPI with UnsafeAPI3 = {
      if (unsafe0 eq null) unsafe0 = new UnsafeAPIV1 {}
      unsafe0
    }
    override def toString: String =
      s"Runtime(environment = $environment, fiberRefs = $fiberRefs, runtimeFlags = ${RuntimeFlags.render(runtimeFlags)})"
  }

  /**
   * A layer that leaves the runtime unchanged: building it prints a deprecation
   * message ("Runtime.addFatal is deprecated & inert, kept only for binary
   * compatibility.") to standard error. Which errors count as fatal is no
   * longer configurable.
   */
  @deprecated("Custom Fatal handling is deprecated, kept only for binary compatibility.", "2.1.22")
  def addFatal(fatal: Class[_ <: Throwable])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer(Console.printLineError("Runtime.addFatal is deprecated & inert, kept only for binary compatibility.").ignore)

  /**
   * A layer that adds `annotation` to the log annotations of the effects it is
   * provided to, so that loggers receive it with each log entry.
   */
  def addLogAnnotation(annotation: LogAnnotation)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentLogAnnotations.locallyScopedWith(_ + (annotation.key -> annotation.value)))

  /**
   * A layer that adds `logger` to the loggers of the effects it is provided to.
   * The default loggers stay: combine it with `removeDefaultLoggers` to replace
   * them. The added logger receives entries of every level, including the
   * levels below `Info` that the default loggers leave out.
   */
  def addLogger(logger: ZLogger[String, Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(ZIO.withLoggerScoped(logger))

  /**
   * A layer that adds `supervisor` to the supervisors of the effects it is
   * provided to. The supervisor sees the fibers that start after the layer is
   * built, such as the fibers those effects fork; when the layer is passed to
   * `Runtime.unsafe.fromLayer`, it also sees the fibers that the runtime starts
   * with `unsafe.run` and `unsafe.fork`. A supervisor that is already present
   * is not added twice.
   */
  def addSupervisor(supervisor: Supervisor[Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentSupervisor.locallyScopedWith(_.patchAdd(supervisor)))

  /**
   * Creates a runtime with the environment `r`, the fiber-local values
   * `fiberRefs0` and the runtime flags `runtimeFlags0`. Pass
   * `RuntimeFlags.default` unless other flags are needed: with
   * `RuntimeFlags.none`, the fibers the runtime starts cannot be interrupted.
   */
  def apply[R](
    r: ZEnvironment[R],
    fiberRefs0: FiberRefs,
    runtimeFlags0: RuntimeFlags
  ): Runtime[R] = new Internal[R](r, fiberRefs0, runtimeFlags0) {}

  /**
   * The runtime with an empty environment, empty `FiberRefs` (so each
   * `FiberRef` has its initial value: the platform's `defaultExecutor`,
   * `defaultBlockingExecutor` and `defaultLoggers`, and no supervisor) and
   * `RuntimeFlags.default`, which enables `Interruption`, `CooperativeYielding`
   * and `FiberRoots`.
   */
  val default: Runtime[Any] =
    Runtime(ZEnvironment.empty, FiberRefs.empty, RuntimeFlags.default)

  /**
   * A layer that disables `flags` for the effects it is provided to.
   *
   * Composed in parallel with `++`, `enableFlags(f)` and `disableFlags(f)` do
   * not apply one after the other: when `f` starts disabled, it ends enabled in
   * either order, and when it starts enabled (as `FiberRoots` does), it ends
   * disabled in either order. With `>>>`, the layer on the right wins.
   */
  def disableFlags(flags: RuntimeFlag*)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.foreachDiscard(flags)(f => ZIO.withRuntimeFlagsScoped(RuntimeFlags.disable(f)))
    }

  /**
   * A layer that enables `RuntimeFlag.CooperativeYielding`, which
   * `RuntimeFlags.default` already enables.
   */
  def enableCooperativeYielding(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    enableFlags(RuntimeFlag.CooperativeYielding)

  /**
   * A layer that enables `RuntimeFlag.CurrentFiber`, so that
   * `Fiber.currentFiber()` returns the fiber running on the current thread.
   */
  def enableCurrentFiber(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    enableFlags(RuntimeFlag.CurrentFiber)

  /**
   * A layer that enables `flags` for the effects it is provided to. See
   * `disableFlags` for what `enableFlags(f) ++ disableFlags(f)` does.
   */
  def enableFlags(flags: RuntimeFlag*)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.foreachDiscard(flags)(f => ZIO.withRuntimeFlagsScoped(RuntimeFlags.enable(f)))
    }

  /**
   * A layer that enables `RuntimeFlag.FiberRoots`, which `RuntimeFlags.default`
   * already enables.
   */
  def enableFiberRoots(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    enableFlags(RuntimeFlag.FiberRoots)

  /**
   * A layer that enables `RuntimeFlag.OpLog`. The ZIO runtime does not read
   * this flag: enabling it changes only what `ZIO.runtimeFlags` reports.
   */
  def enableOpLog(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    enableFlags(RuntimeFlag.OpLog)

  /**
   * A layer that enables `RuntimeFlag.OpSupervision`, so that the `onEffect`
   * method of the current supervisors is called for each operation that a fiber
   * runs.
   */
  def enableOpSupervision(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    enableFlags(RuntimeFlag.OpSupervision)

  /**
   * A layer that enables `RuntimeFlag.RuntimeMetrics`, so that fibers update
   * the metrics in `zio.metrics.Metric.runtime`, such as the number of fibers
   * started and their lifetimes.
   */
  def enableRuntimeMetrics(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    enableFlags(RuntimeFlag.RuntimeMetrics)

  /**
   * A layer that enables `RuntimeFlag.WorkStealing`, which the runtime ignores.
   * `RuntimeFlags.render` and `RuntimeFlags.toSet` do not list this flag.
   */
  @deprecated("Unused + unimplemented: using this flag will have no effect", "2.1.19")
  def enableWorkStealing(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    enableFlags(RuntimeFlag.WorkStealing)

  /**
   * A layer that removes `Runtime.defaultLoggers` from the loggers of the
   * effects it is provided to. Loggers added with `addLogger` stay, so
   * `Runtime.removeDefaultLoggers ++ Runtime.addLogger(logger)` makes `logger`
   * the only logger.
   */
  val removeDefaultLoggers: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentLoggers.locallyScopedWith(_ -- Runtime.defaultLoggers))
  }

  /**
   * A layer that makes `executor` the blocking executor, which runs
   * `ZIO.attemptBlocking` and the other blocking effects, for the effects it is
   * provided to.
   */
  def setBlockingExecutor(executor: Executor)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentBlockingExecutor.locallyScoped(executor))

  /**
   * A layer that makes `configProvider` the source that `ZIO.config` reads for
   * the effects it is provided to.
   */
  def setConfigProvider(configProvider: ConfigProvider)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(ZIO.withConfigProviderScoped(configProvider))

  /**
   * A layer that makes `executor` the executor of the effects it is provided to
   * and of the fibers they fork. A fiber that is already running moves to
   * `executor` at its next yield or asynchronous boundary. Blocking effects
   * such as `ZIO.attemptBlocking` still run on the blocking executor.
   */
  def setExecutor(executor: Executor)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.overrideExecutor.locallyScoped(Some(executor)))

  /**
   * A layer that sets the level at which a fiber logs an error that it did not
   * handle when it ends ("Fiber ... did not handle an error"). The default
   * level is `Debug`, which the default loggers leave out. Fibers that end only
   * because they were interrupted log nothing.
   */
  def setUnhandledErrorLogLevel(logLevel: LogLevel)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.unhandledErrorLogLevel.locallyScoped(Some(logLevel)))

  /**
   * A layer that sets the function that the runtime calls when an effect throws
   * a fatal error, which is a `VirtualMachineError` such as
   * `StackOverflowError`. The function cannot return normally: it throws or
   * ends the program. `Runtime.defaultReportFatal` is the default.
   */
  def setReportFatal(reportFatal: Throwable => Nothing)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentReportFatal.locallyScoped(reportFatal))

  /**
   * Runtime constructors that perform side effects when they are called, so
   * each needs an implicit `Unsafe`.
   */
  object unsafe {

    /**
     * Unsafely creates a `Runtime` from a `ZLayer` whose resources will be
     * allocated immediately, and not released until the `Runtime` is shut down
     * or the end of the application. The runtime has the layer's output as its
     * environment and the settings that the layer changed, such as loggers or
     * runtime flags. Throws a `FiberFailure` when the layer fails. On the JVM,
     * it registers a shutdown hook that releases the resources when the JVM
     * exits; on Scala.js and Scala Native, only `shutdown()` releases them.
     *
     * This method is useful for small applications and integrating ZIO with
     * legacy code, but other applications should investigate using
     * [[ZIO.provide]] directly in their application entry points.
     */
    def fromLayer[R](layer: Layer[Any, R])(implicit trace: Trace, unsafe: Unsafe): Runtime.Scoped[R] = {
      val (runtime, shutdown) = default.unsafe.run {
        Scope.make.flatMap { scope =>
          scope.extend(layer.toRuntime).map { acquire =>
            val finalizer = { () =>
              if (scope.size > 0)
                default.unsafe.run(scope.close(Exit.unit).uninterruptible).getOrThrowFiberFailure()
            }
            val deregisterHook = Platform.addShutdownHookScoped(finalizer)

            val shutdown = { () =>
              try finalizer()
              finally deregisterHook()
            }
            (acquire, shutdown)
          }
        }
      }.getOrThrowFiberFailure()

      Runtime.Scoped(runtime.environment, runtime.fiberRefs, runtime.runtimeFlags, shutdown)
    }
  }

  /**
   * A runtime that reads its environment, `FiberRefs` and runtime flags from
   * `underlying` each time it needs them.
   */
  class Proxy[+R](underlying: Runtime[R]) extends Runtime[R] {

    /** Returns `underlying.environment`. */
    def environment = underlying.environment

    /** Returns `underlying.fiberRefs`. */
    def fiberRefs = underlying.fiberRefs

    /** Returns `underlying.runtimeFlags`. */
    def runtimeFlags = underlying.runtimeFlags
  }

  /**
   * A runtime that can be shutdown to release resources allocated to it.
   */
  final case class Scoped[+R](
    override val environment: ZEnvironment[R],
    override val fiberRefs: FiberRefs,
    override val runtimeFlags: RuntimeFlags,
    shutdown0: () => Unit
  ) extends Internal[R](environment, fiberRefs, runtimeFlags) { self =>
    override final def mapEnvironment[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime.Scoped[R1] =
      Scoped(f(environment), fiberRefs, runtimeFlags, shutdown0)

    /**
     * Adds `shutdown` to the unsafe methods of a `Runtime.Scoped`.
     */
    trait UnsafeAPI2 {

      /**
       * Shuts down this runtime and releases resources allocated to it. Once
       * this runtime has been shut down the behavior of methods on it is
       * undefined and it should be discarded. A second call releases nothing.
       */
      def shutdown()(implicit unsafe: Unsafe): Unit
    }

    override def unsafe: UnsafeAPI with UnsafeAPI2 with UnsafeAPI3 = {
      if (unsafe0 eq null) unsafe0 = new UnsafeAPIV2 {}
      unsafe0.asInstanceOf[UnsafeAPI with UnsafeAPI2 with UnsafeAPI3] // Internal can't access API2
    }
    protected abstract class UnsafeAPIV2 extends UnsafeAPIV1 with UnsafeAPI2 {
      def shutdown()(implicit unsafe: Unsafe) = shutdown0()
    }

  }
}
