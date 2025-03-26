package zio.tagging.syntax

import zio.tagging.internal.ZLayerRequireTagged
import zio._

class ZLayerExtensionsForRequireTagged[RIn, +E, +ROut](private val layer: ZLayer[RIn, E, ROut]) extends AnyVal {

  /**
   * Requires `A` that is part of `R` in `ZLayer[R, _, _]` to be tagged with tag
   * `Tag`
   *
   * Example:
   *
   * {{{
   *   val layers = ZLayer.make[Env](
   *    Endpoint.live.tagged[ServiceA],                 // ◄───┐
   *    Endpoint.live.tagged[ServiceB],                 //     │
   *    External.live.requireTagged[Endpoint, ServiceA] //  ───┘
   *  )
   * }}}
   */
  def requireTagged[A, Tag](implicit impl: ZLayerRequireTagged[RIn, A, Tag]): ZLayer[impl.ROut, E, ROut] =
    impl(layer)
}
