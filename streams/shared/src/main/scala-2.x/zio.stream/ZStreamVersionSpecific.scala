package zio.stream

import zio.internal.macros.LayerMacros
import zio.{ZEnv, ZLayer}

private[zio] trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>

  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val stream: ZStream[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to stream
   * val stream2 : ZStream[ZEnv, Nothing, Unit] = stream.injectCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  def injectCustom[E1 >: E](layers: ZLayer[_, E1, _]*): ZStream[ZEnv, E1, O] =
    macro LayerMacros.injectSomeImpl[ZStream, ZEnv, R, E1, O]

  /**
   * Automatically assembles a layer for the ZStream effect.
   */
  def inject[E1 >: E](layers: ZLayer[_, E1, _]*): ZStream[Any, E1, O] =
    macro LayerMacros.injectImpl[ZStream, R, E1, O]

}
