package zio.tagging.syntax

import zio.ZLayer

import scala.language.implicitConversions

trait ZLayerSyntax {

  implicit def zLayerSyntaxForTagging[RIn, E, ROut](
    layer: ZLayer[RIn, E, ROut]
  ): ZLayerExtensionsForTagging[RIn, E, ROut] =
    new ZLayerExtensionsForTagging(layer)

  implicit def zLayerSyntaxForRequireTagged[RIn, E, ROut](
    layer: ZLayer[RIn, E, ROut]
  ): ZLayerExtensionsForRequireTagged[RIn, E, ROut] =
    new ZLayerExtensionsForRequireTagged(layer)

  implicit def zLayerProvideSomeOps[RIn, E, ROut](
    layer: ZLayer[RIn, E, ROut]
  ): ZLayerFixGreaterGreaterGreater[RIn, E, ROut] =
    new ZLayerFixGreaterGreaterGreater(layer)
}
