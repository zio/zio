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
package data

abstract class Ref2[F[+_, +_], A] {

  /**
   * Reads the `Ref` value.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def get: F[Nothing, A]

  /**
   * Sets the `Ref` value.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def set(a: A): F[Nothing, Unit]

  /**
   * Sets the `Ref` value eventually.
   * The value might not immediately equal `a`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def setAsync(a: A): F[Nothing, Unit]

  /**
   * Updates the `Ref` applying `f`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def update(f: A => A): F[Nothing, A]

  /**
   * Updates the `Ref` if `pf` is defined for
   * the current `Ref`'s value. It leaves the
   * `Ref` unchanged otherwise.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def updateSome(pf: PartialFunction[A, A]): F[Nothing, A]

  /**
   * Updates the `Ref` applying `f` and returns a value
   * of type `B` as result of the modification
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def modify[B](f: A => (B, A)): F[Nothing, B]

  /**
   * Updates the `Ref` applying `pf` and returns a value
   * of type `B` as result of the modification. If `pf`
   * is not defined for the current `Ref`'s value it returns
   * `default`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): F[Nothing, B]
}
