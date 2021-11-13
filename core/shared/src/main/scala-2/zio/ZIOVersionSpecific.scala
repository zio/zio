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

private[zio] trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `ZEnv`, leaving an effect that only depends on the `ZEnv`. This will
   * also satisfy transitive `ZEnv` requirements with `ZEnv.any`, allowing them
   * to be provided later.
   *
   * {{{
   * val zio: ZIO[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyServiceBuilder and Console to zio
   * val zio2 : ZIO[ZEnv, Nothing, Unit] = zio.injectCustom(oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  def injectCustom[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZIO[ZEnv, E1, A] =
    macro ServiceBuilderMacros.injectSomeImpl[ZIO, ZEnv, R, E1, A]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified service builder and leaving the remainder `R0`.
   *
   * {{{
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Clock] = ???
   *
   * val zio: ZIO[Clock with Random, Nothing, Unit] = ???
   *
   * val zio2 = zio.injectSome[Random](clockServiceBuilder)
   * }}}
   */
  def injectSome[R0 <: Has[_]]: ProvideSomeServicesPartiallyApplied[R0, R, E, A] =
    new ProvideSomeServicesPartiallyApplied[R0, R, E, A](self)

  /**
   * Automatically assembles a service builder for the ZIO effect.
   */
  def inject[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZIO[Any, E1, A] =
    macro ServiceBuilderMacros.injectImpl[ZIO, R, E1, A]

}

private final class ProvideSomeServicesPartiallyApplied[R0 <: Has[_], -R, +E, +A](val self: ZIO[R, E, A])
    extends AnyVal {

  def provideServices[E1 >: E, R1](
    serviceBuilder: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R], trace: ZTraceElement): ZIO[R0, E1, A] =
    self.provideServices(serviceBuilder)

  @deprecated("use provideServices", "2.0.0")
  def provideLayer[E1 >: E, R1](
    layer: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R], trace: ZTraceElement): ZIO[R0, E1, A] =
    provideServices(layer)

  def provideSomeServices[R0 <: Has[_]]: ZIO.ProvideSomeServices[R0, R, E, A] =
    new ZIO.ProvideSomeServices[R0, R, E, A](self)

  @deprecated("use provideSomeServices", "2.0.0")
  def provideSomeLayer[R0 <: Has[_]]: ZIO.ProvideSomeServices[R0, R, E, A] =
    provideSomeServices

  def apply[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZIO[R0, E1, A] =
    macro ServiceBuilderMacros.injectSomeImpl[ZIO, R0, R, E1, A]
}
