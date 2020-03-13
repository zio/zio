/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio.internal.Tracing
import zio.internal.tracing.{ TracingConfig, ZIOFn }
import zio.internal.{ Executor, FiberContext, Platform, PlatformConstants }

/**
 * A `Runtime[R]` is capable of executing tasks within an environment `R`.
 */
trait Runtime[+R] {

  /**
   * The environment of the runtime.
   */
  val environment: R

  /**
   * The platform of the runtime, which provides the essential capabilities
   * necessary to bootstrap execution of tasks.
   */
  val platform: Platform

  /**
   * Constructs a new `Runtime` by mapping the environment.
   */
  def map[R1](f: R => R1): Runtime[R1] = Runtime(f(environment), platform)

  /**
   * Constructs a new `Runtime` by mapping the platform.
   */
  def mapPlatform(f: Platform => Platform): Runtime[R] = Runtime(environment, f(platform))

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
   * Executes the Task/RIO effect synchronously, failing
   * with the original `Throwable` on both [[Cause.Fail]] and [[Cause.Die]].
   * In addition, appends a new element to the `Throwable`s "caused by" chain,
   * with this `Cause` "pretty printed" (in stackless mode) as the message.
   * May fail on Scala.js if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be done at the edges of your program.
   */
  final def unsafeRunTask[A](task: => ZIO[R, Throwable, A]): A =
    unsafeRunSync(task).fold(cause => throw cause.squashTrace, identity)

  /**
   * Executes the effect synchronously. May
   * fail on Scala.js if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunSync[E, A](zio: => ZIO[R, E, A]): Exit[E, A] = {
    val result = internal.OneShot.make[Exit[E, A]]

    unsafeRunAsync(zio)((x: Exit[E, A]) => result.set(x))

    result.get()
  }

  /**
   * Executes the effect asynchronously,
   * eventually passing the exit value to the specified callback.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunAsync[E, A](zio: => ZIO[R, E, A])(k: Exit[E, A] => Any): Unit = {
    val InitialInterruptStatus = InterruptStatus.Interruptible

    val fiberId = Fiber.newFiberId()

    lazy val context: FiberContext[E, A] = new FiberContext[E, A](
      fiberId,
      platform,
      environment.asInstanceOf[AnyRef],
      platform.executor,
      InitialInterruptStatus,
      None,
      PlatformConstants.tracingSupported,
      Platform.newWeakHashMap()
    )

    Fiber.track(context)

    context.evaluateNow(ZIOFn.recordStackTrace(() => zio)(zio.asInstanceOf[IO[E, A]]))
    context.runAsync(k)

    ()
  }

  /**
   * Executes the effect asynchronously, discarding the result of execution.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunAsync_[E, A](zio: ZIO[R, E, A]): Unit =
    unsafeRunAsync(zio)(_ => ())

  /**
   * Runs the IO, returning a Future that will be completed when the effect has been executed.
   *
   * This method is effectful and should only be used at the edges of your program.
   */
  final def unsafeRunToFuture[E <: Throwable, A](zio: ZIO[R, E, A]): CancelableFuture[A] =
    unsafeRun(zio.forkDaemon >>= (_.toFuture))

  /**
   * Constructs a new `Runtime` with the specified new environment.
   */
  def as[R1](r1: R1): Runtime[R1] = map(_ => r1)

  /**
   * Constructs a new `Runtime` with the specified executor.
   */
  def withExecutor(e: Executor): Runtime[R] = mapPlatform(_.withExecutor(e))

  /**
   * Constructs a new `Runtime` with the specified fatal predicate.
   */
  def withFatal(f: Throwable => Boolean): Runtime[R] = mapPlatform(_.withFatal(f))

  /**
   * Constructs a new `Runtime` with the fatal error reporter.
   */
  def withReportFatal(f: Throwable => Nothing): Runtime[R] = mapPlatform(_.withReportFatal(f))

  /**
   * Constructs a new `Runtime` with the specified error reporter.
   */
  def withReportFailure(f: Cause[Any] => Unit): Runtime[R] = mapPlatform(_.withReportFailure(f))

  /**
   * Constructs a new `Runtime` with the specified tracer and tracing configuration.
   */
  def withTracing(t: Tracing): Runtime[R] = mapPlatform(_.withTracing(t))

  /**
   * Constructs a new `Runtime` with the specified tracing configuration.
   */
  def withTracingConfig(config: TracingConfig): Runtime[R] = mapPlatform(_.withTracingConfig(config))
}

object Runtime {

  /**
   * A runtime that can be shutdown to release resources allocated to it.
   */
  trait Managed[+R] extends Runtime[R] {

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
      mapPlatform(_.withExecutor(e))

    override final def withFatal(f: Throwable => Boolean): Runtime.Managed[R] =
      mapPlatform(_.withFatal(f))

    override final def withReportFatal(f: Throwable => Nothing): Runtime.Managed[R] =
      mapPlatform(_.withReportFatal(f))

    override final def withReportFailure(f: Cause[Any] => Unit): Runtime.Managed[R] =
      mapPlatform(_.withReportFailure(f))

    override final def withTracing(t: Tracing): Runtime.Managed[R] =
      mapPlatform(_.withTracing(t))

    override final def withTracingConfig(config: TracingConfig): Runtime.Managed[R] =
      mapPlatform(_.withTracingConfig(config))
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

  lazy val default: Runtime[ZEnv] = Runtime(ZEnv.Services.live, Platform.default)

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
  def unsafeFromLayer[R <: Has[_]](
    layer: Layer[Any, R],
    platform: Platform = Platform.default
  ): Runtime.Managed[R] = {
    val runtime = Runtime((), platform)
    val (environment, shutdown) = runtime.unsafeRun {
      layer.build.reserve.flatMap {
        case Reservation(acquire, release) =>
          Ref.make(true).flatMap { finalize =>
            val finalizer = () =>
              runtime.unsafeRun {
                release(Exit.unit).whenM(finalize.getAndSet(false)).uninterruptible
              }
            UIO.effectTotal(Platform.addShutdownHook(finalizer)) *>
              acquire.map((_, finalizer))
          }
      }
    }
    Runtime.Managed(environment, platform, shutdown)
  }
}
