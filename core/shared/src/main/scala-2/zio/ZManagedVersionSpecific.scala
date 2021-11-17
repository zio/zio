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

import zio.internal.macros.ServiceBuilderMacros

private[zio] trait ZManagedVersionSpecific[-R, +E, +A] { self: ZManaged[R, E, A] =>

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `ZEnv`, leaving an effect that only depends on the `ZEnv`. This will
   * also satisfy transitive `ZEnv` requirements with `ZEnv.any`, allowing them
   * to be provided later.
   *
   * {{{
   * val managed: ZManaged[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyServiceBuilder and Console to managed
   * val managed2 : ZManaged[ZEnv, Nothing, Unit] = managed.injectCustom(oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  def injectCustom[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZManaged[ZEnv, E1, A] =
    macro ServiceBuilderMacros.injectSomeImpl[ZManaged, ZEnv, R, E1, A]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified service builder and leaving the remainder `R0`.
   *
   * {{{
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Clock] = ???
   *
   * val managed: ZManaged[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockServiceBuilder)
   * }}}
   */
  def injectSome[R0]: ProvideSomeServiceBuilderManagedPartiallyApplied[R0, R, E, A] =
    new ProvideSomeServiceBuilderManagedPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a service builder for the ZManaged effect.
   */
  def inject[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZManaged[Any, E1, A] =
    macro ServiceBuilderMacros.injectImpl[ZManaged, R, E1, A]

}

private final class ProvideSomeServiceBuilderManagedPartiallyApplied[R0, -R, +E, +A](
  val self: ZManaged[R, E, A]
) extends AnyVal {

  def provideServices[E1 >: E, R1](
    serviceBuilder: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R], trace: ZTraceElement): ZManaged[R0, E1, A] =
    self.provideServices(serviceBuilder)

  @deprecated("use provideServices", "2.0.0")
  def provideLayer[E1 >: E, R1](
    layer: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R], trace: ZTraceElement): ZManaged[R0, E1, A] =
    provideServices(layer)

  def provideSome[R0]: ZManaged.ProvideSome[R0, R, E, A] =
    new ZManaged.ProvideSome[R0, R, E, A](self)

  @deprecated("use provideSome", "2.0.0")
  def provideSomeLayer[R0]: ZManaged.ProvideSome[R0, R, E, A] =
    provideSome

  def apply[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZManaged[R0, E1, A] =
    macro ServiceBuilderMacros.injectSomeImpl[ZManaged, R0, R, E1, A]
}
