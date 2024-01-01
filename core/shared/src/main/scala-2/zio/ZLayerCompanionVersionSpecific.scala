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

import zio.internal.macros.{DummyK, ZLayerMakeMacros}

private[zio] trait ZLayerCompanionVersionSpecific {

  /**
   * Automatically assembles a layer for the provided type.
   *
   * {{{
   * ZLayer.make[Car](carLayer, wheelsLayer, engineLayer)
   * }}}
   */
  def make[R]: MakePartiallyApplied[R] =
    new MakePartiallyApplied[R]

  /**
   * Automatically constructs a layer for the provided type `R`, leaving a
   * remainder `R0`.
   *
   * {{{
   * val carLayer: ZLayer[Engine with Wheels, Nothing, Car] = ???
   * val wheelsLayer: ZLayer[Any, Nothing, Wheels] = ???
   *
   * val layer = ZLayer.makeSome[Engine, Car](carLayer, wheelsLayer)
   * }}}
   */
  def makeSome[R0, R]: MakeSomePartiallyApplied[R0, R] =
    new MakeSomePartiallyApplied[R0, R]

  /**
   * Automatically derives a simple layer for the provided type.
   *
   * {{{
   * class Car(wheels: Wheels, engine: Engine) { /* ... */ }
   *
   * val carLayer: URLayer[Wheels & Engine, Car] = ZLayer.derive[Car]
   * }}}
   */
  def derive[A]: ZLayer[Nothing, Any, A] = macro zio.internal.macros.ZLayerDerivationMacros.deriveImpl[A]
}

final class MakePartiallyApplied[R](val dummy: Boolean = true) extends AnyVal {
  def apply[E](
    layer: ZLayer[_, E, _]*
  )(implicit dummyKRemainder: DummyK[Any], dummyK: DummyK[R]): ZLayer[Any, E, R] =
    macro ZLayerMakeMacros.makeImpl[E, Any, R]
}

final class MakeSomePartiallyApplied[R0, R](
  val dummy: Boolean = true
) extends AnyVal {
  def apply[E](
    layer: ZLayer[_, E, _]*
  )(implicit dummyKRemainder: DummyK[R0], dummyK: DummyK[R]): ZLayer[R0, E, R] =
    macro ZLayerMakeMacros.makeSomeImpl[E, R0, R]
}
