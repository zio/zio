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

import zio.internal.macros.{DummyK, ZLayerFromAutoMacros}

private[zio] trait ZLayerCompanionVersionSpecific {

  /**
   * Automatically assembles a layer for the provided type.
   *
   * {{{
   * ZLayer.fromAuto[Car](carLayer, wheelsLayer, engineLayer)
   * }}}
   */
  def fromAuto[R <: Has[_]]: FromLayerAutoPartiallyApplied[R] =
    new FromLayerAutoPartiallyApplied[R]

  /**
   * Automatically assembles a layer for the provided type `R`, leaving
   * a remainder `R0`.
   *
   * {{{
   * val carLayer: ZLayer[Engine with Wheels, Nothing, Car] = ???
   * val wheelsLayer: ZLayer[Any, Nothing, Wheels] = ???
   *
   * val layer = ZLayer.fromSomeAuto[Engine, Car](carLayer, wheelsLayer)
   * }}}
   */

  def fromSomeAuto[R0 <: Has[_], R <: Has[_]]: FromSomeAutoPartiallyApplied[R0, R] =
    new FromSomeAutoPartiallyApplied[R0, R]

}

private[zio] final class FromLayerAutoPartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  def apply[E](layers: ZLayer[_, E, _]*)(implicit dummyKRemainder: DummyK[Any], dummyK: DummyK[R]): ZLayer[Any, E, R] =
    macro ZLayerFromAutoMacros.fromAutoImpl[E, Any, R]
}

private[zio] final class FromSomeAutoPartiallyApplied[R0 <: Has[_], R <: Has[_]](
  val dummy: Boolean = true
) extends AnyVal {
  def apply[E](layers: ZLayer[_, E, _]*)(implicit dummyKRemainder: DummyK[R0], dummyK: DummyK[R]): ZLayer[R0, E, R] =
    macro ZLayerFromAutoMacros.fromAutoImpl[E, R0, R]
}
