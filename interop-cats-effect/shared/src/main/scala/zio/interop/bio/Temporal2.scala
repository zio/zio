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

import java.time.Instant

import scala.concurrent.duration.Duration

abstract class Temporal2[F[+_, +_]] extends Bracket2[F] {

  /**
   * Creates an effect that succeeds with the `Instant`
   * taken when it begins execution.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def now: F[Nothing, Instant]

  /**
   * Creates an effect that will sleep for the given `duration` before finishing execution.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def sleep(duration: Duration): F[Nothing, Unit]
}

object Temporal2 {

  @inline def apply[F[+_, +_]: Temporal2]: Temporal2[F] = implicitly
}
