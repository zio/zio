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

abstract class RunAsync2[F[+_, +_]] extends Async2[F] {

  /**
   * Returns an effect that runs asynchronously `fa` and completes with
   * the call back k if `fa` succeeds or fails. If fa is interrupted or
   * dead the callback will not be executed and the resulting effect `G`
   * will also be interrupted or dead.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def runAsync[G[+_, +_], E, A](fa: F[E, A], k: Either[E, A] => G[Nothing, Unit])(
    implicit AG: Async2[G]
  ): G[Nothing, Unit]
}

object RunAsync2 {

  @inline def apply[F[+_, +_]: RunAsync2]: RunAsync2[F] = implicitly
}
