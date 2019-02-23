/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio

import scalaz.zio.internal.{ FiberContext, Platform }

/**
 * A `Runtime[R]` is capable of executing tasks within an environment `R`.
 */
trait Runtime[+R] {

  /**
   * The environment of the runtime.
   */
  val Environment: R

  /**
   * The platform of the runtime, which provides the essential capabilities
   * necessary to bootstrap execution of tasks.
   */
  val Platform: Platform

  /**
   * Executes the effect synchronously, failing
   * with [[scalaz.zio.FiberFailure]] if there are any errors. May fail on
   * Scala.js if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be done at the edges of your program.
   */
  final def unsafeRun[E, A](zio: ZIO[R, E, A]): A =
    unsafeRunSync(zio).getOrElse(c => throw FiberFailure(c))

  /**
   * Executes the effect synchronously. May
   * fail on Scala.js if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be invoked at the edges of your program.
   */
  final def unsafeRunSync[E, A](zio: ZIO[R, E, A]): Exit[E, A] = {
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
  final def unsafeRunAsync[E, A](zio: ZIO[R, E, A])(k: Exit[E, A] => Unit): Unit = {
    val context = new FiberContext[E, A](Platform)

    context.evaluateNow(zio.provide(Environment))
    context.runAsync(k)
  }

  /**
   * Executes the effect asynchronously,
   * discarding the result of execution.
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
  final def unsafeRunToFuture[E <: Throwable, A](io: IO[E, A]): scala.concurrent.Future[A] =
    unsafeRun(io.toFuture)
}

object Runtime {

  /**
   * Builds a new runtime given an environment `R` and a [[scalaz.zio.internal.Platform]].
   */
  final def apply[R](r: R, platform: Platform): Runtime[R] = new Runtime[R] {
    val Environment = r
    val Platform    = platform
  }
}
