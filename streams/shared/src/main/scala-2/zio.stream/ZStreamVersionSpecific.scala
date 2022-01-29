package zio.stream

import zio.internal.macros.LayerMacros
import zio.{NeedsEnv, ZEnv, ZLayer}

private[stream] trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>

  /**
   * Automatically constructs the part of the environment that is not part of
   * the `ZEnv`, leaving an effect that only depends on the `ZEnv`. This will
   * also satisfy transitive `ZEnv` requirements with `ZEnv.any`, allowing them
   * to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.provideCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  def provideCustom[E1 >: E](layer: ZLayer[_, E1, _]*): ZStream[ZEnv, E1, O] =
    macro LayerMacros.provideCustomImpl[ZStream, ZEnv, R, E1, O]

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val managed: ZStream[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.provideSome[Random](clockLayer)
   * }}}
   */
  def provideSome[R0]: ProvideSomeLayerStreamPartiallyApplied[R0, R, E, O] =
    new ProvideSomeLayerStreamPartiallyApplied[R0, R, E, O](self)

  /**
   * Automatically assembles a layer for the ZStream effect.
   */
  def provide[E1 >: E](layer: ZLayer[_, E1, _]*): ZStream[Any, E1, O] =
    macro LayerMacros.provideImpl[ZStream, R, E1, O]

}

private final class ProvideSomeLayerStreamPartiallyApplied[R0, -R, +E, +O](
  val self: ZStream[R, E, O]
) extends AnyVal {
  def provideLayer[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): ZStream[R0, E1, O] =
    self.provideLayer(layer)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): ZStream[R0, E1, O] =
    macro LayerMacros.provideSomeImpl[ZStream, R0, R, E1, O]
}
