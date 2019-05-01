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
package interop
package bio
package data

abstract class Deferred2[F[+ _, + _], E, A] {

  /**
   * Retrieves the value of the `Deferred` waiting if the status
   * is empty.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def await: F[E, A]

  /**
   * Creates an effect that completes the `Deferred` with the
   * specified value. The effect returns `true` if the state
   * wasn't already set and returns false without changing
   * the state otherwise.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def succeed(a: A): F[Nothing, Boolean]

  /**
   * Creates an effect that fails the deferred with the
   * error `e` and returns a value `true`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def fail(e: E): F[E, Boolean]
}
