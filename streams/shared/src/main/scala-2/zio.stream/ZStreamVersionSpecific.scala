package zio.stream

import zio.internal.macros.DepsMacros
import zio.{Has, NeedsEnv, ZEnv, ZDeps}

private[stream] trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>

  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZDeps[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZDeps[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.injectCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  def injectCustom[E1 >: E](deps: ZDeps[_, E1, _]*): ZStream[ZEnv, E1, O] =
    macro DepsMacros.injectSomeImpl[ZStream, ZEnv, R, E1, O]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layers and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZDeps[Any, Nothing, Clock] = ???
   *
   * val managed: ZStream[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.injectSome[Random](clockLayer)
   * }}}
   */
  def injectSome[R0 <: Has[_]]: ProvideSomeLayerStreamPartiallyApplied[R0, R, E, O] =
    new ProvideSomeLayerStreamPartiallyApplied[R0, R, E, O](self)

  /**
   * Automatically assembles a layer for the ZStream effect.
   */
  def inject[E1 >: E](deps: ZDeps[_, E1, _]*): ZStream[Any, E1, O] =
    macro DepsMacros.injectImpl[ZStream, R, E1, O]

}

private final class ProvideSomeLayerStreamPartiallyApplied[R0 <: Has[_], -R, +E, +O](val self: ZStream[R, E, O])
    extends AnyVal {
  def provideLayer[E1 >: E, R1](
    layer: ZDeps[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): ZStream[R0, E1, O] =
    self.provideDeps(layer)

  def apply[E1 >: E](deps: ZDeps[_, E1, _]*): ZStream[R0, E1, O] =
    macro DepsMacros.injectSomeImpl[ZStream, R0, R, E1, O]
}
