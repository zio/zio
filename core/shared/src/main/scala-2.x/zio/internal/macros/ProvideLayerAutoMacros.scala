package zio.internal.macros

import zio._

import scala.reflect.macros.blackbox

class ProvideLayerAutoMacros(val c: blackbox.Context) extends AutoLayerMacroUtils {
  import c.universe._

  def provideLayerAutoImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[Any, E, A]] = {
    assertProperVarArgs(layers)
    val expr = buildMemoizedLayer(generateExprGraph(layers), getRequirements[R])
    c.Expr[F[Any, E, A]](q"${c.prefix}.provideLayer(${expr.tree})")
  }

  def provideCustomLayerAutoImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[ZEnv, E, A]] = {
    assertProperVarArgs(layers)
    val ZEnvRequirements = getRequirements[ZEnv]
    val requirements     = getRequirements[R] diff ZEnvRequirements

    val zEnvLayer = Node(List.empty, ZEnvRequirements, reify(ZEnv.any))
    val nodes     = (zEnvLayer +: layers.map(getNode)).toList

    val layerExpr = buildMemoizedLayer(generateExprGraph(nodes), requirements)
    c.Expr[F[ZEnv, E, A]](q"${c.prefix}.provideCustomLayer(${layerExpr.tree})")
  }
}
