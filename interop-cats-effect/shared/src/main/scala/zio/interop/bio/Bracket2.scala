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

import zio.interop.bio.Failed.Interrupt

abstract class Bracket2[F[+_, +_]] extends Guaranteed2[F] with Errorful2[F] { self =>

  /**
   * Returns an effect that will acquire a resource and will release
   * it after the execution of `use` regardless the fact that `use`
   * succeed or fail.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def bracket[E, A, B](
    acquire: F[E, A],
    release: (A, Either[Failed[E], B]) => F[Nothing, Unit]
  )(use: A => F[E, B]): F[E, B]

  /**
   * Executes the `cleanup` effect only if `fa` is interrupted
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def onInterrupt[E, A](fa: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A] = {

    def onRelease[AA]: (AA, Either[Failed[_], A]) => F[Nothing, Unit] = {
      case (_, Left(Interrupt)) => cleanup
      case _                    => monad.unit
    }

    bracket(monad.unit, onRelease)(_ => fa)
  }
}
