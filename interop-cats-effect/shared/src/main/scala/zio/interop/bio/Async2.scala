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

abstract class Async2[F[+_, +_]] extends Sync2[F] {

  /**
   * Imports into `F` an asynchronous effect with the possibility to
   * return the value synchronously.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def asyncMaybe[E, A](k: (F[E, A] => Unit) => Option[F[E, A]]): F[E, A]

  /**
   * Imports into `F` an asynchronous effect with the possibility to
   * return the value synchronously.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def asyncF[E, A](k: (F[E, A] => Unit) => F[Nothing, Unit]): F[E, A]

  /**
   * Imports into `F` an asynchronous effect.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def async[E, A](k: (F[E, A] => Unit) => Unit): F[E, A] =
    asyncMaybe { callback =>
      k(callback)
      None
    }

  /**
   * Returns an effect `F` that never completes execution.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def never: F[Nothing, Nothing] =
    async(_ => ())
}

object Async2 {

  @inline def apply[F[+_, +_]: Async2]: Async2[F] = implicitly
}
