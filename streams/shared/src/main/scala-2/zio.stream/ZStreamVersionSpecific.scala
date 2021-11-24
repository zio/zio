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
  def provideCustom[E1 >: E](layers: ZLayer[_, E1, _]*): ZStream[ZEnv, E1, O] =
    macro LayerMacros.provideSomeImpl[ZStream, ZEnv, R, E1, O]

  /**
   * Automatically assembles a layer for the ZStream effect.
   */
  def provide[E1 >: E](layers: ZLayer[_, E1, _]*): ZStream[Any, E1, O] =
    macro LayerMacros.provideImpl[ZStream, R, E1, O]

}

final class ProvideSomeStreamPartiallyApplied[R0, -R, +E, +O](
  val self: ZStream[R, E, O]
) extends AnyVal {
  def provide[E1 >: E](
    layer: ZLayer[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): ZStream[R0, E1, O] =
    self.provide(layer)

  def apply[E1 >: E](layers: ZLayer[_, E1, _]*): ZStream[R0, E1, O] =
    macro LayerMacros.provideSomeImpl[ZStream, R0, R, E1, O]
}
