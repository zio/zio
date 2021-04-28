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
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to zio
   * val zio2 : ZIO[ZEnv, Nothing, Unit] = zio.injectCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  def injectCustom[E1 >: E](layers: ZLayer[_, E1, _]*): ZIO[ZEnv, E1, A] =
    macro LayerMacros.injectSomeImpl[ZIO, ZEnv, R, E1, A]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layers and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val zio: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val zio2 = zio.injectSome[Random](clockLayer)
   * }}}
   */
  def injectSome[R0 <: Has[_]]: ProvideSomeLayerPartiallyApplied[R0, R, E, A] =
    new ProvideSomeLayerPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a layer for the ZIO effect.
   */
  def inject[E1 >: E](layers: ZLayer[_, E1, _]*): ZIO[Any, E1, A] =
    macro LayerMacros.injectImpl[ZIO, R, E1, A]

}

private final class ProvideSomeLayerPartiallyApplied[R0 <: Has[_], -R, +E, +A](val self: ZIO[R, E, A]) extends AnyVal {
  def provideLayerManual[E1 >: E, R1](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): ZIO[R0, E1, A] =
    self.provideLayerManual(layer)

  def provideSomeLayerManual[R0 <: Has[_]]: ZIO.ProvideSomeLayer[R0, R, E, A] =
    new ZIO.ProvideSomeLayer[R0, R, E, A](self)

  def apply[E1 >: E](layers: ZLayer[_, E1, _]*): ZIO[R0, E1, A] =
    macro LayerMacros.injectSomeImpl[ZIO, R0, R, E1, A]
}
