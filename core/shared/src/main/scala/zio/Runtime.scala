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
  final def map[R1](f: R => R1): Runtime[R1] = Runtime(f(environment), platform)

  /**
   * Constructs a new `Runtime` by mapping the platform.
   */
  final def mapPlatform(f: Platform => Platform): Runtime[R] = Runtime(environment, f(platform))

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
  final def unsafeRunAsync[E, A](zio: => ZIO[R, E, A])(k: Exit[E, A] => Unit): Unit = {
    val InitialInterruptStatus = InterruptStatus.Interruptible

    val fiberId = Fiber.newFiberId()

    lazy val context: FiberContext[E, A] = new FiberContext[E, A](
      fiberId,
      null,
      platform,
      environment.asInstanceOf[AnyRef],
      platform.executor,
      InitialInterruptStatus,
      false,
      None,
      PlatformConstants.tracingSupported,
      Platform.newWeakHashMap()
    )

    Fiber.track(context)

    context.evaluateNow(ZIOFn.recordStackTrace(() => zio)(zio.asInstanceOf[IO[E, A]]))
    context.runAsync(k)
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
  final def unsafeRunToFuture[E <: Throwable, A](io: ZIO[R, E, A]): CancelableFuture[E, A] =
    unsafeRun(io.toFuture)

  /**
   * Constructs a new `Runtime` with the specified new environment.
   */
  final def as[R1](r1: R1): Runtime[R1] = map(_ => r1)

  /**
   * Constructs a new `Runtime` with the specified executor.
   */
  final def withExecutor(e: Executor): Runtime[R] = mapPlatform(_.withExecutor(e))

  /**
   * Constructs a new `Runtime` with the specified fatal predicate.
   */
  final def withFatal(f: Throwable => Boolean): Runtime[R] = mapPlatform(_.withFatal(f))

  /**
   * Constructs a new `Runtime` with the fatal error reporter.
   */
  final def withReportFatal(f: Throwable => Nothing): Runtime[R] = mapPlatform(_.withReportFatal(f))

  /**
   * Constructs a new `Runtime` with the specified error reporter.
   */
  final def withReportFailure(f: Cause[Any] => Unit): Runtime[R] = mapPlatform(_.withReportFailure(f))

  /**
   * Constructs a new `Runtime` with the specified tracer and tracing configuration.
   */
  final def withTracing(t: Tracing): Runtime[R] = mapPlatform(_.withTracing(t))

  /**
   * Constructs a new `Runtime` with the specified tracing configuration.
   */
  final def withTracingConfig(config: TracingConfig): Runtime[R] = mapPlatform(_.withTracingConfig(config))
}

object Runtime {
  lazy val default = Runtime((), Platform.default)

  /**
   * Builds a new runtime given an environment `R` and a [[zio.internal.Platform]].
   */
  def apply[R](r: R, platform0: Platform): Runtime[R] = new Runtime[R] {
    val environment = r
    val platform    = platform0
  }
}
