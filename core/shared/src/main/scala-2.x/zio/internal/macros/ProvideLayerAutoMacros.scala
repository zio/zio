package zio.internal.macros

import zio._

import scala.reflect.macros.blackbox

class ProvideLayerAutoMacros(val c: blackbox.Context) extends MacroUtils with ExprGraphModule {
  import c.universe._

  def provideLayerAutoImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[ZIO[Any, E, A]] = {
    assertProperVarArgs(layers)
    val layerExpr = ExprGraph.buildLayer[R](layers.map(getNode).toList)
    c.Expr[ZIO[Any, E, A]](q"${c.prefix}.provideLayer(${layerExpr.tree})")
  }

  def provideCustomLayerAutoImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[ZIO[ZEnv, E, A]] = {
    assertProperVarArgs(layers)
    val ZEnvRequirements = getRequirements[ZEnv]
    val requirements     = getRequirements[R] diff ZEnvRequirements

    val zEnvLayer = Node(List.empty, ZEnvRequirements, reify(ZEnv.any))
    val nodes     = (zEnvLayer +: layers.map(getNode)).toList

    val layerExpr = ExprGraph(nodes).buildLayerFor(requirements)
    c.Expr[ZIO[ZEnv, E, A]](q"${c.prefix}.provideCustomLayer(${layerExpr.tree})")
  }
}
