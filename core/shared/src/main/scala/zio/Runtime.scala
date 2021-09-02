/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.internal.tracing.ZIOFn
import zio.internal.{Executor, FiberContext, Platform, PlatformConstants, Tracing}

import scala.concurrent.Future

/**
 * A `Runtime[R]` is capable of executing tasks within an environment `R`.
 */
trait Runtime[+R] {

  /**
   * The environment of the runtime.
   */
  def environment: R

  /**
   * The platform of the runtime, which provides the essential capabilities
   * necessary to bootstrap execution of tasks.
   */
  def platform: Platform

  /**
   * Constructs a new `Runtime` with the specified new environment.
   */
  def as[R1](r1: R1): Runtime[R1] =
    map(_ => r1)

  /**
   * Constructs a new `Runtime` by mapping the environment.
   */
  def map[R1](f: R => R1): Runtime[R1] =
    Runtime(f(environment), platform)

  /**
   * Constructs a new `Runtime` by mapping the platform.
   */
  def mapPlatform(f: Platform => Platform): Runtime[R] =
    Runtime(environment, f(platform))

  /**
   * Runs the effect "purely" through an async boundary. Useful for testing.
   */
  final def run[E, A](zio: ZIO[R, E, A]): IO[E, A] =
    IO.async[E, A] { callback =>
      unsafeRunAsyncWith(zio)(exit => callback(ZIO.done(exit)))
    }

  /**
   * Executes the effect synchronously, failing
   * with [[zio.FiberFailure]] if there are any errors. May fail on
   * Scala.js if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be done at the edges of your program.
   */
  final def unsafeRun[E, A](zio: => ZIO[R, E, A]): A =
    unsafeRunSync(zio).getOrElse(c => throw FiberFailure(c))

  /**
   * Executes the effect asynchronously, discarding the result of execution.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunAsync[E, A](zio: ZIO[R, E, A]): Unit =
    unsafeRunAsyncWith(zio)(_ => ())

  /**
   * Executes the effect asynchronously,
   * eventually passing the exit value to the specified callback.
   * It returns a callback, which can be used to interrupt the running execution.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunSync[E, A](zio0: => ZIO[R, E, A]): Exit[E, A] =
    defaultUnsafeRunSync(zio0) // tryFastUnsafeRunSync(zio0, 0)

  protected final def defaultUnsafeRunSync[E, A](zio: => ZIO[R, E, A]): Exit[E, A] = {
    val result = internal.OneShot.make[Exit[E, A]]

    unsafeRunWith(zio)(result.set)

    result.get()
  }

  protected def tryFastUnsafeRunSync[E, A](zio: ZIO[R, E, A], stack: Int): Exit[E, A] =
    if (stack >= 50) defaultUnsafeRunSync(zio)
    else {
      type Erased = ZIO[Any, Any, Any]
      def erase[R, E, A](zio: ZIO[R, E, A]): Erased = zio.asInstanceOf[ZIO[Any, Any, Any]]
      type K = Any => Erased
      def eraseK[R, E, A, B](f: A => ZIO[R, E, B]): K = f.asInstanceOf[K]

      val nullK = null.asInstanceOf[K]

      var curZio               = erase(zio)
      var x1, x2, x3, x4       = nullK
      var done: Exit[Any, Any] = null.asInstanceOf[Exit[Any, Any]]

      while (done eq null) {
        try {
          curZio.tag match {
            case ZIO.Tags.FlatMap =>
              val zio = curZio.asInstanceOf[ZIO.FlatMap[R, E, Any, A]]

              curZio = erase(zio.zio)

              val k = eraseK(zio.k)

              if (x1 eq null) x1 = k
              else if (x2 eq null) {
                x2 = x1; x1 = k
              } else if (x3 eq null) {
                x3 = x2; x2 = x1; x1 = k
              } else if (x4 eq null) {
                x4 = x3; x3 = x2; x2 = x1; x1 = k
              } else {
                // Our "register"-based stack can't handle it, try consuming more JVM stack:
                val exit = tryFastUnsafeRunSync(zio, stack + 1)

                curZio = exit match {
                  case Exit.Failure(cause) => ZIO.failCause(cause)
                  case Exit.Success(value) => ZIO.succeedNow(value)
                }
              }

            case ZIO.Tags.Succeed =>
              val zio = curZio.asInstanceOf[ZIO.Succeed[A]]

              if (x1 ne null) {
                val k = x1
                x1 = x2; x2 = x3; x3 = x4; x4 = nullK
                curZio = k(zio.value)
              } else {
                done = Exit.succeed(zio.value)
              }

            case ZIO.Tags.Fail =>
              val zio = curZio.asInstanceOf[ZIO.Fail[E]]

              done = Exit.failCause(zio.fill(() => null))

            case ZIO.Tags.EffectTotal =>
              val zio = curZio.asInstanceOf[ZIO.EffectTotal[A]]

              if (x1 ne null) {
                val k = x1
                x1 = x2; x2 = x3; x3 = x4; x4 = nullK
                curZio = k(zio.effect())
              } else {
                val value = zio.effect()

                done = Exit.succeed(value)
              }

            case _ =>
              val zio = curZio

              // Give up, the mini-interpreter can't handle it:
              curZio = defaultUnsafeRunSync(zio) match {
                case Exit.Failure(cause) => ZIO.failCause(cause)
                case Exit.Success(value) => ZIO.succeedNow(value)
              }
          }
        } catch {
          case ZIO.ZioError(e) =>
            done = Exit.fail(e)

          case t: Throwable if !platform.fatal(t) =>
            done = Exit.die(t)
        }
      }

      done.asInstanceOf[Exit[E, A]]
    }

  /**
   * Executes the effect asynchronously,
   * eventually passing the exit value to the specified callback.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunAsyncWith[E, A](zio: => ZIO[R, E, A])(k: Exit[E, A] => Any): Unit = {
    unsafeRunAsyncCancelable(zio)(k)
    ()
  }

  /**
   * Executes the effect asynchronously,
   * eventually passing the exit value to the specified callback.
   * It returns a callback, which can be used to interrupt the running execution.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunAsyncCancelable[E, A](zio: => ZIO[R, E, A])(k: Exit[E, A] => Any): FiberId => Exit[E, A] = {
    lazy val curZio = zio
    val canceler    = unsafeRunWith(curZio)(k)
    fiberId => {
      val result = internal.OneShot.make[Exit[E, A]]
      canceler(fiberId)(result.set)
      result.get()
    }
  }

  /**
   * Executes the Task/RIO effect synchronously, failing
   * with the original `Throwable` on both [[Cause.Fail]] and [[Cause.Die]].
   * In addition, appends a new element to the `Throwable`s "caused by" chain,
   * with this `Cause` "pretty printed" (in stackless mode) as the message.
   * May fail on Scala.js if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be done at the edges of your program.
   */
  final def unsafeRunTask[A](task: => RIO[R, A]): A =
    unsafeRunSync(task).fold(cause => throw cause.squashTrace, identity)

  /**
   * Runs the IO, returning a Future that will be completed when the effect has been executed.
   *
   * This method is effectful and should only be used at the edges of your program.
   */
  final def unsafeRunToFuture[E <: Throwable, A](zio: ZIO[R, E, A]): CancelableFuture[A] = {
    val p: concurrent.Promise[A] = scala.concurrent.Promise[A]()

    val canceler = unsafeRunWith(zio)(_.fold(cause => p.failure(cause.squashTraceWith(identity)), p.success))

    new CancelableFuture[A](p.future) {
      def cancel(): Future[Exit[Throwable, A]] = {
        val p: concurrent.Promise[Exit[Throwable, A]] = scala.concurrent.Promise[Exit[Throwable, A]]()
        canceler(FiberId.None)(p.success)
        p.future
      }
    }
  }

  /**
   * Constructs a new `Runtime` with the specified blocking executor.
   */
  def withBlockingExecutor(e: Executor): Runtime[R] = mapPlatform(_.copy(blockingExecutor = e))

  /**
   * Constructs a new `Runtime` with the specified executor.
   */
  def withExecutor(e: Executor): Runtime[R] = mapPlatform(_.copy(executor = e))

  /**
   * Constructs a new `Runtime` with the specified fatal predicate.
   */
  def withFatal(f: Throwable => Boolean): Runtime[R] = mapPlatform(_.copy(fatal = f))

  /**
   * Constructs a new `Runtime` with the fatal error reporter.
   */
  def withReportFatal(f: Throwable => Nothing): Runtime[R] = mapPlatform(_.copy(reportFatal = f))

  /**
   * Constructs a new `Runtime` with the specified error reporter.
   */
  def withReportFailure(f: Cause[Any] => UIO[Unit]): Runtime[R] = mapPlatform(_.copy(reportFailure = f))

  /**
   * Constructs a new `Runtime` with the specified tracer and tracing configuration.
   */
  def withTracing(t: Tracing): Runtime[R] = mapPlatform(_.copy(tracing = t))

  private final def unsafeRunWith[E, A](
    zio: => ZIO[R, E, A]
  )(k: Exit[E, A] => Any): FiberId => (Exit[E, A] => Any) => Unit = {
    val fiberId = Fiber.newFiberId()

    val scope = ZScope.unsafeMake[Exit[E, A]]()

    val supervisor = platform.supervisor

    lazy val context: FiberContext[E, A] = new FiberContext[E, A](
      fiberId,
      platform,
      environment.asInstanceOf[AnyRef],
      platform.executor,
      false,
      InterruptStatus.Interruptible,
      None,
      PlatformConstants.tracingSupported,
      new java.util.concurrent.atomic.AtomicReference(Map.empty),
      scope,
      platform.reportFailure
    )

    if (supervisor ne Supervisor.none) {
      supervisor.unsafeOnStart(environment, zio, None, context)

      context.onDone(exit => supervisor.unsafeOnEnd(exit.flatten, context))
    }

    context.nextEffect = ZIOFn.recordStackTrace(() => zio)(zio.asInstanceOf[IO[E, A]])
    context.run()
    context.awaitAsync(k)

    fiberId =>
      k => unsafeRunAsyncWith(context.interruptAs(fiberId))((exit: Exit[Nothing, Exit[E, A]]) => k(exit.flatten))
  }
}

object Runtime {
  class Proxy[+R](underlying: Runtime[R]) extends Runtime[R] {
    def platform    = underlying.platform
    def environment = underlying.environment
  }

  /**
   * A runtime that can be shutdown to release resources allocated to it.
   */
  abstract class Managed[+R] extends Runtime[R] {

    /**
     * Shuts down this runtime and releases resources allocated to it. Once
     * this runtime has been shut down the behavior of methods on it is
     * undefined and it should be discarded.
     */
    def shutdown(): Unit

    override final def as[R1](r1: R1): Runtime.Managed[R1] =
      map(_ => r1)

    override final def map[R1](f: R => R1): Runtime.Managed[R1] =
      Managed(f(environment), platform, () => shutdown())

    override final def mapPlatform(f: Platform => Platform): Runtime.Managed[R] =
      Managed(environment, f(platform), () => shutdown())

    override final def withExecutor(e: Executor): Runtime.Managed[R] =
      mapPlatform(_.copy(executor = e))

    override final def withFatal(f: Throwable => Boolean): Runtime.Managed[R] =
      mapPlatform(_.copy(fatal = f))

    override final def withReportFatal(f: Throwable => Nothing): Runtime.Managed[R] =
      mapPlatform(_.copy(reportFatal = f))

    override final def withReportFailure(f: Cause[Any] => UIO[Unit]): Runtime.Managed[R] =
      mapPlatform(_.copy(reportFailure = f))

    override final def withTracing(t: Tracing): Runtime.Managed[R] =
      mapPlatform(_.copy(tracing = t))
  }

  object Managed {

    /**
     * Builds a new managed runtime given an environment `R`, a
     * [[zio.internal.Platform]], and a shut down action.
     */
    def apply[R](r: R, platform0: Platform, shutdown0: () => Unit): Runtime.Managed[R] =
      new Runtime.Managed[R] {
        val environment = r
        val platform    = platform0
        def shutdown()  = shutdown0()
      }
  }

  /**
   * Builds a new runtime given an environment `R` and a [[zio.internal.Platform]].
   */
  def apply[R](r: R, platform0: Platform): Runtime[R] = new Runtime[R] {
    val environment = r
    val platform    = platform0
  }

  /**
   * The default [[Runtime]] for most ZIO applications. This runtime is configured with
   * the default environment, containing standard services, as well as the default
   * platform, which is optimized for typical ZIO applications.
   */
  lazy val default: Runtime[ZEnv] = Runtime(ZEnv.Services.live, Platform.default)

  /**
   * The global [[Runtime]], which piggybacks atop the global execution context available
   * to Scala applications. Use of this runtime is not generally recommended, unless the
   * intention is to avoid creating any thread pools or other resources.
   */
  lazy val global: Runtime[ZEnv] = Runtime(ZEnv.Services.live, Platform.global)

  /**
   * Unsafely creates a `Runtime` from a `ZLayer` whose resources will be
   * allocated immediately, and not released until the `Runtime` is shut down
   * or the end of the application.
   *
   * This method is useful for small applications and integrating ZIO with
   * legacy code, but other applications should investigate using
   * [[ZIO.provideLayer]] directly in their application entry points.
   */
  def unsafeFromLayer[R](layer: Layer[Any, R], platform: Platform = Platform.default): Runtime.Managed[R] = {
    val runtime = Runtime((), platform)
    val (environment, shutdown) = runtime.unsafeRun {
      ZManaged.ReleaseMap.make.flatMap { releaseMap =>
        layer.build.zio.provide(((), releaseMap)).flatMap { case (_, acquire) =>
          val finalizer = () =>
            runtime.unsafeRun {
              releaseMap.releaseAll(Exit.unit, ExecutionStrategy.Sequential).uninterruptible.unit
            }

          UIO.succeed(Platform.addShutdownHook(finalizer)).as((acquire, finalizer))
        }
      }
    }

    Runtime.Managed(environment, platform, shutdown)
  }
}
