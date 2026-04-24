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

package zio

import zio.internal.macros.LayerMacros

private[zio] trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val zio: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val zio2 = zio.provideSome[Random](clockLayer)
   * }}}
   */
  def provideSome[R0]: ProvideSomeLayerPartiallyApplied[R0, R, E, A] =
    new ProvideSomeLayerPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a layer for the ZIO effect.
   */
  def provide[E1 >: E](layer: ZLayer[_, E1, _]*): ZIO[Any, E1, A] =
    macro LayerMacros.provideImpl[ZIO, R, E1, A]

}

final class ProvideSomeLayerPartiallyApplied[R0, -R, +E, +A](val self: ZIO[R, E, A]) extends AnyVal {

  def provideLayer[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit trace: Trace): ZIO[R0, E1, A] =
    self.provideLayer(layer)

  def provideSomeLayer[R0]: ZIO.ProvideSomeLayer[R0, R, E, A] =
    new ZIO.ProvideSomeLayer[R0, R, E, A](self)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): ZIO[R0, E1, A] =
    macro LayerMacros.provideSomeImpl[ZIO, R0, R, E1, A]
}
