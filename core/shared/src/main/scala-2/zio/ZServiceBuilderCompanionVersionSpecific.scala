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

import zio.internal.macros.{DummyK, WireMacros}

private[zio] trait ZServiceBuilderCompanionVersionSpecific {

  /**
   * Automatically assembles a service builder for the provided type.
   *
   * {{{
   * ZServiceBuilder.wire[Car](carServiceBuilder, wheelsServiceBuilder, engineServiceBuilder)
   * }}}
   */
  def wire[R <: Has[_]]: WirePartiallyApplied[R] =
    new WirePartiallyApplied[R]

  /**
   * Automatically constructs a service builder for the provided type `R`,
   * leaving a remainder `R0`.
   *
   * {{{
   * val carServiceBuilder: ZServiceBuilder[Engine with Wheels, Nothing, Car] = ???
   * val wheelsServiceBuilder: ZServiceBuilder[Any, Nothing, Wheels] = ???
   *
   * val serviceBuilder = ZServiceBuilder.wireSome[Engine, Car](carServiceBuilder, wheelsServiceBuilder)
   * }}}
   */
  def wireSome[R0 <: Has[_], R <: Has[_]]: WireSomePartiallyApplied[R0, R] =
    new WireSomePartiallyApplied[R0, R]

  /**
   * Automatically constructs a service builder for the provided type `R`,
   * leaving a remainder `ZEnv`. This will satisfy all transitive `ZEnv`
   * requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyServiceBuilder and Console to zio
   * val serviceBuilder : ZServiceBuilder[ZEnv, Nothing, OldLady] = ZServiceBuilder.wireCustom[OldLady](oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  def wireCustom[R <: Has[_]]: WireSomePartiallyApplied[ZEnv, R] =
    new WireSomePartiallyApplied[ZEnv, R]

}

private[zio] final class WirePartiallyApplied[R <: Has[_]](val dummy: Boolean = true) extends AnyVal {
  def apply[E](
    serviceBuilder: ZServiceBuilder[_, E, _]*
  )(implicit dummyKRemainder: DummyK[Any], dummyK: DummyK[R]): ZServiceBuilder[Any, E, R] =
    macro WireMacros.wireImpl[E, Any, R]
}

private[zio] final class WireSomePartiallyApplied[R0 <: Has[_], R <: Has[_]](
  val dummy: Boolean = true
) extends AnyVal {
  def apply[E](
    serviceBuilder: ZServiceBuilder[_, E, _]*
  )(implicit dummyKRemainder: DummyK[R0], dummyK: DummyK[R]): ZServiceBuilder[R0, E, R] =
    macro WireMacros.wireImpl[E, R0, R]
}
