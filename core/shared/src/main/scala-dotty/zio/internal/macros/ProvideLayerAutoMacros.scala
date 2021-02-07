package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

import AutoLayerMacroUtils._

object ProvideLayerAutoMacros {
  def provideLayerAutoImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZIO[R0,E,A]] = {
    val layerExpr = fromAutoImpl[R0, R, E](layers)
    '{$zio.provideLayer($layerExpr)}
  }

  def fromAutoImpl[In: Type, Out: Type, E: Type](layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZLayer[In,E,Out]] = {
    val deferredRequirements = intersectionTypes[In]
    val requirements     = intersectionTypes[Out] 

    val zEnvLayer = Node(List.empty, deferredRequirements, '{ZLayer.requires[In]})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    buildMemoizedLayer(ZLayerExprBuilder(nodes), requirements)
      .asInstanceOf[Expr[ZLayer[In,E,Out]]]
  }

  def fromAutoDebugImpl[Out: Type, E: Type](layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZLayer[Any,E,Out]] = {
    import quotes.reflect._
    val expr = buildLayerFor[Out](layers)
    '{$expr.asInstanceOf[ZLayer[Any, E, Out]]}

    val graph        = ZLayerExprBuilder(layers)
    val requirements = intersectionTypes[Out]
    graph.buildLayerFor(requirements)

    val graphString: String = 
      graph.graph
        .map(layer => RenderedGraph(renderExpr(layer)))
        .buildComplete(requirements)
        .toOption
        .get
        .fold[RenderedGraph](RenderedGraph.Row(List.empty), identity, _ ++ _, _ >>> _).render

    val maxWidth = graphString.maxLineWidth
    val title    = "Layer Graph Visualization"
    val adjust   = (maxWidth - title.length) / 2

    val rendered = "\n" + (" " * adjust) + title.yellow.underlined + "\n\n" + graphString + "\n\n"

    report.throwError(rendered)
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