package zio.stream

import zio.internal.macros.LayerMacros
import zio.ZLayer

private[stream] trait ZStreamVersionSpecific[-R, +E, +O] { self: ZStream[R, E, O] =>

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val stream: ZStream[Clock with Random, Nothing, Unit] = ???
   *
   * val stream2 = stream.provideSome[Random](clockLayer)
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

final class ProvideSomeLayerStreamPartiallyApplied[R0, -R, +E, +O](
  val self: ZStream[R, E, O]
) extends AnyVal {
  def provideLayer[E1 >: E](
    layer: ZLayer[R0, E1, R]
  ): ZStream[R0, E1, O] =
    self.provideLayer(layer)

  def apply[E1 >: E](layer: ZLayer[_, E1, _]*): ZStream[R0, E1, O] =
    macro LayerMacros.provideSomeImpl[ZStream, R0, R, E1, O]
}
