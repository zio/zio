package zio.internal.macros

import zio._

import scala.reflect.macros.blackbox

class ProvideLayerMacros(val c: blackbox.Context) extends AutoLayerMacroUtils {
  import c.universe._

  def provideLayerImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[Any, E, A]] = {
    assertProperVarArgs(layers)
    val expr = buildMemoizedLayer(generateExprGraph(layers), getRequirements[R])
    c.Expr[F[Any, E, A]](q"${c.prefix}.provideLayerManual(${expr.tree})")
  }

  def provideSomeLayerImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[R0, E, A]] = {
    assertProperVarArgs(layers)
    val remainderRequirements = getRequirements[R0]
    val requirements          = getRequirements[R] diff remainderRequirements

    val remainderExpr = reify(ZLayer.requires[R0])
    val remainderNode = Node(List.empty, remainderRequirements, remainderExpr)
    val nodes         = (remainderNode +: layers.map(getNode)).toList

    val layerExpr = buildMemoizedLayer(generateExprGraph(nodes), requirements)
    c.Expr[F[R0, E, A]](q"${c.prefix}.provideLayerManual(${layerExpr.tree} ++ ${remainderExpr.tree})")
  }
}
