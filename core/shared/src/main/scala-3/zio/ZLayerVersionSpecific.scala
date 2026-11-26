package zio

import zio.internal.macros.LayerMacros

private[zio] transparent trait ZLayerVersionSpecific[-R, +E, +A] { self: ZLayer[R, E, A] =>

  /**
   * Returns an effect that builds this layer, whose output is `Unit`, with its
   * requirements wired at compile time from the given layers, as with
   * `ZLayer.make`. The work of this layer happens while it is built. Its
   * resources are released as soon as the build completes, and then those of
   * the given layers.
   */
  inline def runWith[E1 >: E](
    inline layer: ZLayer[_, E1, _]*
  )(using ev: A IsSubtypeOfOutput Unit): ZIO[Any, E1, Unit] =
    ${ LayerMacros.runWithImpl[R, E1]('{ self.asInstanceOf[ZLayer[R, E, Unit]] }, 'layer) }
}
