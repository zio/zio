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

import zio.internal.macros.ProviderMacros

private[zio] trait ZManagedVersionSpecific[-R, +E, +A] { self: ZManaged[R, E, A] =>

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `ZEnv`, leaving an effect that only depends on the `ZEnv`. This will
   * also satisfy transitive `ZEnv` requirements with `ZEnv.any`, allowing them
   * to be provided later.
   *
   * {{{
   * val managed: ZManaged[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyProvider: ZProvider[Fly, Nothing, OldLady] = ???
   * val flyProvider: ZProvider[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyProvider and Console to managed
   * val managed2 : ZManaged[ZEnv, Nothing, Unit] = managed.injectCustom(oldLadyProvider, flyProvider)
   * }}}
   */
  def injectCustom[E1 >: E](provider: ZProvider[_, E1, _]*): ZManaged[ZEnv, E1, A] =
    macro ProviderMacros.injectSomeImpl[ZManaged, ZEnv, R, E1, A]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified provider and leaving the remainder `R0`.
   *
   * {{{
   * val clockProvider: ZProvider[Any, Nothing, Clock] = ???
   *
   * val managed: ZManaged[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockProvider)
   * }}}
   */
  def injectSome[R0]: ProvideSomeProviderManagedPartiallyApplied[R0, R, E, A] =
    new ProvideSomeProviderManagedPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a provider for the ZManaged effect.
   */
  def inject[E1 >: E](provider: ZProvider[_, E1, _]*): ZManaged[Any, E1, A] =
    macro ProviderMacros.injectImpl[ZManaged, R, E1, A]

}

private final class ProvideSomeProviderManagedPartiallyApplied[R0, -R, +E, +A](
  val self: ZManaged[R, E, A]
) extends AnyVal {

  def provide[E1 >: E, R1](
    provider: ZProvider[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R], trace: ZTraceElement): ZManaged[R0, E1, A] =
    self.provide(provider)

  @deprecated("use provide", "2.0.0")
  def provideLayer[E1 >: E, R1](
    layer: ZProvider[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R], trace: ZTraceElement): ZManaged[R0, E1, A] =
    provide(layer)

  @deprecated("use provide", "2.0.0")
  def provideServices[E1 >: E, R1](
    layer: ZProvider[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R], trace: ZTraceElement): ZManaged[R0, E1, A] =
    provide(layer)

  def provideSome[R0]: ZManaged.ProvideSome[R0, R, E, A] =
    new ZManaged.ProvideSome[R0, R, E, A](self)

  @deprecated("use provideSome", "2.0.0")
  def provideSomeLayer[R0]: ZManaged.ProvideSome[R0, R, E, A] =
    provideSome

  @deprecated("use provideSome", "2.0.0")
  def provideSomeServices[R0]: ZManaged.ProvideSome[R0, R, E, A] =
    provideSome

  def apply[E1 >: E](provider: ZProvider[_, E1, _]*): ZManaged[R0, E1, A] =
    macro ProviderMacros.injectSomeImpl[ZManaged, R0, R, E1, A]
}
