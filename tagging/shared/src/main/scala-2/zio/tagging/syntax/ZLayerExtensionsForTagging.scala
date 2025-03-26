package zio.tagging.syntax

import zio.tagging.tag
import zio.tagging.tag.@@
import zio._

class ZLayerExtensionsForTagging[-RIn, +E, ROut](private val layer: ZLayer[RIn, E, ROut]) extends AnyVal {

  /**
   * Transforms `ZLayer[R, E, A]` to `ZLayer[R, E, A @@ LayerTag]`
   *
   * {{{
   * Pureconfig.loadLayer[Endpoint]("serviceA").tagged[ServiceA]
   * Pureconfig.loadLayer[Endpoint]("serviceB").tagged[ServiceB]
   * }}}
   */
  def tagged[LayerTag](implicit
    ROut: Tag[ROut],
    taggedOut: Tag[ROut @@ LayerTag]
  ): ZLayer[RIn, E, ROut @@ LayerTag] =
    layer.fresh >>> ZLayer.fromFunction((in: ROut) => tag[LayerTag](in))
}
