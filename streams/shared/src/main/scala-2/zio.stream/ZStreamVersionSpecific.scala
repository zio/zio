package zio.stream

import zio.internal.macros.ServiceBuilderMacros
import zio.{Has, NeedsEnv, ZEnv, ZServiceBuilder}

private[stream] trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `ZEnv`, leaving an effect that only depends on the `ZEnv`. This will
   * also satisfy transitive `ZEnv` requirements with `ZEnv.any`, allowing them
   * to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyServiceBuilder: ZServiceBuilder[Fly, Nothing, OldLady] = ???
   * val flyServiceBuilder: ZServiceBuilder[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyServiceBuilder and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.injectCustom(oldLadyServiceBuilder, flyServiceBuilder)
   * }}}
   */
  def injectCustom[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZStream[ZEnv, E1, O] =
    macro ServiceBuilderMacros.injectSomeImpl[ZStream, ZEnv, R, E1, O]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified service builder and leaving the remainder `R0`.
   *
   * {{{
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Clock] = ???
   *
   * val managed: ZStream[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockServiceBuilder)
   * }}}
   */
  def injectSome[R0 <: Has[_]]: ProvideSomeServiceBuilderStreamPartiallyApplied[R0, R, E, O] =
    new ProvideSomeServiceBuilderStreamPartiallyApplied[R0, R, E, O](self)

  /**
   * Automatically assembles a service builder for the ZStream effect.
   */
  def inject[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZStream[Any, E1, O] =
    macro ServiceBuilderMacros.injectImpl[ZStream, R, E1, O]

}

private final class ProvideSomeServiceBuilderStreamPartiallyApplied[R0 <: Has[_], -R, +E, +O](
  val self: ZStream[R, E, O]
) extends AnyVal {
  def provideService[E1 >: E, R1](
    serviceBuilder: ZServiceBuilder[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): ZStream[R0, E1, O] =
    self.provideService(serviceBuilder)

  def apply[E1 >: E](serviceBuilder: ZServiceBuilder[_, E1, _]*): ZStream[R0, E1, O] =
    macro ServiceBuilderMacros.injectSomeImpl[ZStream, R0, R, E1, O]
}
