/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.managed

import zio._

import zio.internal.macros.LayerMacros

private[managed] trait ZManagedVersionSpecific[-R, +E, +A] { self: ZManaged[R, E, A] =>

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val managed: ZManaged[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.provideSome[Random](clockLayer)
   * }}}
   */
  def provideSome[R0]: ProvideSomeLayerManagedPartiallyApplied[R0, R, E, A] =
    new ProvideSomeLayerManagedPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a layer for the ZManaged effect.
   */
  def provide[E1 >: E](layer: ZLayer[_, E1, _]*): ZManaged[Any, E1, A] =
    macro LayerMacros.provideImpl[ZManaged, R, E1, A]

}

final class ProvideSomeLayerManagedPartiallyApplied[R0, -R, +E, +A](
  val self: ZManaged[R, E, A]
) extends AnyVal {

  def provideLayer[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit trace: ZTraceElement): ZManaged[R0, E1, A] =
    self.provideLayer(layer)

  def provideSomeLayer[R0]: ZManaged.ProvideSomeLayer[R0, R, E, A] =
    new ZManaged.ProvideSomeLayer[R0, R, E, A](self)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): ZManaged[R0, E1, A] =
    macro LayerMacros.provideSomeImpl[ZManaged, R0, R, E1, A]
}
