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

abstract class Interruption2[F[+_, +_]] {

  /**
   * Performs `fa` uninterruptedly. This will prevent it from
   * being terminated externally, but it may still fail for internal
   * reasons.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def uninterruptible[E, A](fa: F[E, A]): F[E, A]
}
