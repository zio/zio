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

abstract class Fiber2[F[+_, +_], E, A] { self =>

  def await: F[Nothing, Either[Failed[E], A]]

  def cancel: F[Nothing, Either[Failed[E], A]]

  /**
   * The fiber running the invocation to `join` will suspend waiting for the result of the
   * `self` fiber. If the joined fiber `self` completes in error the resulting effect `F[E, A]`
   * will complete with an error, if the joined fiber `self` succeeds the effect will
   * return an `A` and if the joining fiber or the `self` fiber are interrupted the returned
   * effect will also be interrupted.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def join: F[E, A]
}
