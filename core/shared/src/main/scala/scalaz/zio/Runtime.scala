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

import scalaz.zio.platform.Platform

/**
 * A `Runtime[R]` is capable of executing tasks within an environment `R`.
 * Runtimes are not functional, but they are necessary for final translation of
 * a `ZIO` value into the effects that it models. For best results, push
 * execution of tasks to the edge of the application, such as the application
 * main function. See [[scalaz.zio.App]] for an example of this approach.
 */
trait Runtime[R <: Platform] {

  /**
   * The environment of the runtime.
   */
  val Environment: R

  /**
   * Awaits for the result of the fiber to be computed.
   * In Javascript, this operation will not, in general, succeed because it is not possible to block for the result.
   * However, it may succeed in some cases if the IO is purely synchronous.
   */
  final def unsafeRun[E, A](io: ZIO[R, E, A]): A =
    io.unsafeRun(Environment)

  /**
   * Awaits for the result of the fiber to be computed.
   * In Javascript, this operation will not, in general, succeed because it is not possible to block for the result.
   * However, it may succeed in some cases if the IO is purely synchronous.
   */
  final def unsafeRunSync[E, A](io: ZIO[R, E, A]): Exit[E, A] =
    io.unsafeRunSync(Environment)

  /**
   * Runs the `io` asynchronously.
   */
  final def unsafeRunAsync[E, A](io: ZIO[R, E, A])(k: Exit[E, A] => Unit): Unit =
    io.unsafeRunAsync(Environment, k)
}
