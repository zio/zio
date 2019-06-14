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

import zio.interop.bio.data.{ Deferred2, Ref2 }

abstract class ConcurrentData2[F[+_, +_]] {

  /**
   * Creates a `Ref2[F, A]` where the value is set to `a`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def ref[A](a: A): F[Nothing, Ref2[F, A]]

  /**
   * Creates a `Deferred2[F, E, A]`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def deferred[E, A]: F[Nothing, Deferred2[F, E, A]]
}
