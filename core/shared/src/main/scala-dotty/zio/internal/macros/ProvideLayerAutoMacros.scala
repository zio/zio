package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

import AutoLayerMacroUtils._

object ProvideLayerAutoMacros {
  def provideLayerImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZIO[R0,E,A]] = {
    val layerExpr = fromAutoImpl[R0, R, E](layers)
    '{$zio.provideLayerManual($layerExpr.asInstanceOf[ZLayer[R0,E,R]])}
  }

  def fromAutoImpl[In: Type, Out: Type, E: Type](layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZLayer[In,E,Out]] = {
    val deferredRequirements = getRequirements[In]
    val requirements     = getRequirements[Out]

    val zEnvLayer = Node(List.empty, deferredRequirements, '{ZLayer.requires[In]})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    buildMemoizedLayer(ZLayerExprBuilder(nodes), requirements)
      .asInstanceOf[Expr[ZLayer[In,E,Out]]]
  }
}


trait ExprGraphCompileVariants { self : ZLayerExprBuilder.type =>
  def apply(layers: Expr[Seq[ZLayer[_,_,_]]])(using ctx: Quotes): ZLayerExprBuilder[LayerExpr] = 
    apply(getNodes(layers))

  def apply(nodes: List[Node[LayerExpr]])(using ctx: Quotes): ZLayerExprBuilder[LayerExpr] = {
    import ctx.reflect._

    def compileError(message: String) : Nothing = report.throwError(message)
    def empty: LayerExpr = '{ZLayer.succeed(())}
    def composeH(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{$lhs.asInstanceOf[ZLayer[_,_,Has[_]]] +!+ $rhs.asInstanceOf[ZLayer[_,_,Has[_]]]}
    def composeV(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{$lhs.asInstanceOf[ZLayer[_,_,Has[Unit]]] >>> $rhs.asInstanceOf[ZLayer[Has[Unit],_,_]]}

    ZLayerExprBuilder[LayerExpr](
      Graph(nodes),
      renderExpr,
      compileError,
      empty,
      composeH,
      composeV
      )
  }
}