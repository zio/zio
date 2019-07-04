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

package zio
package interop
package bio

import scala.util.Try

abstract class Sync2[F[+_, +_]] extends Guaranteed2[F] with Errorful2[F] {

  /**
   * Lazily lifts a pure value into the effect `F`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def delay[A](a: => A): F[Nothing, A]

  /**
   * Allows to construct an effect from a lazily provided value
   * that can be itself effectful.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def suspend[E, A](fa: => F[E, A]): F[E, A] =
    monad flatten delay(fa)

  /**
   * Lazily lifts a thunk into the effect `F` and translates
   * any `NonFatal` into a `Throwable` failure of the effect.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def delayNonFatal[E, A](thunk: => A): F[Throwable, A] =
    suspend((Try.apply[A] _ andThen fromTry[A])(thunk))
}

object Sync2 {

  @inline def apply[F[+_, +_]: Sync2]: Sync2[F] = implicitly
}
