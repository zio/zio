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
 */
trait Runtime[R <: Platform] {

  /**
   * The environment of the runtime.
   */
  val Environment: R

  /**
   * Synchronously executes the task. This may fail on Scala.js.
   */
  final def unsafeRun[E, A](io: ZIO[R, E, A]): A =
    io.unsafeRun(Environment)

  /**
   * Synchronously executes the task. This may fail on Scala.js.
   */
  final def unsafeRunSync[E, A](io: ZIO[R, E, A]): Exit[E, A] =
    io.unsafeRunSync(Environment)

  /**
   * Asynchronously executes the task.
   */
  final def unsafeRunAsync[E, A](io: ZIO[R, E, A])(k: Exit[E, A] => Unit): Unit =
    io.unsafeRunAsync(Environment, k)
}
