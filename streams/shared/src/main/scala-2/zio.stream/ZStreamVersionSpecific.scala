package zio.stream

import zio.internal.macros.ProviderMacros
import zio.{NeedsEnv, ZEnv, ZProvider}

private[stream] trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `ZEnv`, leaving an effect that only depends on the `ZEnv`. This will
   * also satisfy transitive `ZEnv` requirements with `ZEnv.any`, allowing them
   * to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyProvider: ZProvider[Fly, Nothing, OldLady] = ???
   * val flyProvider: ZProvider[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyProvider and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.injectCustom(oldLadyProvider, flyProvider)
   * }}}
   */
  def injectCustom[E1 >: E](provider: ZProvider[_, E1, _]*): ZStream[ZEnv, E1, O] =
    macro ProviderMacros.injectSomeImpl[ZStream, ZEnv, R, E1, O]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified provider and leaving the remainder `R0`.
   *
   * {{{
   * val clockProvider: ZProvider[Any, Nothing, Clock] = ???
   *
   * val managed: ZStream[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockProvider)
   * }}}
   */
  def injectSome[R0]: ProvideSomeStreamPartiallyApplied[R0, R, E, O] =
    new ProvideSomeStreamPartiallyApplied[R0, R, E, O](self)

  /**
   * Automatically assembles a provider for the ZStream effect.
   */
  def inject[E1 >: E](provider: ZProvider[_, E1, _]*): ZStream[Any, E1, O] =
    macro ProviderMacros.injectImpl[ZStream, R, E1, O]

}

private final class ProvideSomeStreamPartiallyApplied[R0, -R, +E, +O](
  val self: ZStream[R, E, O]
) extends AnyVal {
  def provide[E1 >: E, R1](
    provider: ZProvider[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): ZStream[R0, E1, O] =
    self.provide(provider)

  def apply[E1 >: E](provider: ZProvider[_, E1, _]*): ZStream[R0, E1, O] =
    macro ProviderMacros.injectSomeImpl[ZStream, R0, R, E1, O]
}
