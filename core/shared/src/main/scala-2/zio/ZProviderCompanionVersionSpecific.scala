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

private[zio] trait ZProviderCompanionVersionSpecific {

  /**
   * Automatically assembles a provider for the provided type.
   *
   * {{{
   * ZProvider.wire[Car](carProvider, wheelsProvider, engineProvider)
   * }}}
   */
  def wire[R]: WirePartiallyApplied[R] =
    new WirePartiallyApplied[R]

  /**
   * Automatically constructs a provider for the provided type `R`, leaving a
   * remainder `R0`.
   *
   * {{{
   * val carProvider: ZProvider[Engine with Wheels, Nothing, Car] = ???
   * val wheelsProvider: ZProvider[Any, Nothing, Wheels] = ???
   *
   * val provider = ZProvider.wireSome[Engine, Car](carProvider, wheelsProvider)
   * }}}
   */
  def wireSome[R0, R]: WireSomePartiallyApplied[R0, R] =
    new WireSomePartiallyApplied[R0, R]

  /**
   * Automatically constructs a provider for the provided type `R`, leaving a
   * remainder `ZEnv`. This will satisfy all transitive `ZEnv` requirements with
   * `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val oldLadyProvider: ZProvider[Fly, Nothing, OldLady] = ???
   * val flyProvider: ZProvider[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyProvider and Console to zio
   * val provider : ZProvider[ZEnv, Nothing, OldLady] = ZProvider.wireCustom[OldLady](oldLadyProvider, flyProvider)
   * }}}
   */
  def wireCustom[R]: WireSomePartiallyApplied[ZEnv, R] =
    new WireSomePartiallyApplied[ZEnv, R]

}

private[zio] final class WirePartiallyApplied[R](val dummy: Boolean = true) extends AnyVal {
  def apply[E](
    provider: ZProvider[_, E, _]*
  )(implicit dummyKRemainder: DummyK[Any], dummyK: DummyK[R]): ZProvider[Any, E, R] =
    macro WireMacros.wireImpl[E, Any, R]
}

private[zio] final class WireSomePartiallyApplied[R0, R](
  val dummy: Boolean = true
) extends AnyVal {
  def apply[E](
    provider: ZProvider[_, E, _]*
  )(implicit dummyKRemainder: DummyK[R0], dummyK: DummyK[R]): ZProvider[R0, E, R] =
    macro WireMacros.wireImpl[E, R0, R]
}
