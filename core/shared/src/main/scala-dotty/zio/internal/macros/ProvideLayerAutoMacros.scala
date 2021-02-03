package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

import AutoLayerMacroUtils._

object ProvideLayerAutoMacros {
  def provideLayerAutoImpl[R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZIO[Any,E,A]] = {
    val expr = buildLayerFor[R](layers)
    '{$zio.provideLayer($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def provideCustomLayerAutoImpl[R <: Has[?], E, A]
  (zio: Expr[ZIO[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes, Type[R], Type[E], Type[A]): Expr[ZIO[ZEnv,E,A]] = {
    val ZEnvRequirements = intersectionTypes[ZEnv]
    val requirements     = intersectionTypes[R] 

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{ZEnv.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = buildMemoizedLayer(ExprGraph(nodes), requirements)

    '{$zio.asInstanceOf[ZIO[Has[Unit], E, A]].provideLayer(ZEnv.any >>> $expr.asInstanceOf[ZLayer[ZEnv, E, Has[Unit]]])}
  }

  def fromAutoImpl[Out: Type, E: Type](layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZLayer[Any,E,Out]] = {
    val expr = buildLayerFor[Out](layers)
    '{$expr.asInstanceOf[ZLayer[Any, E, Out]]}
  }

  def fromAutoDebugImpl[Out: Type, E: Type](layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZLayer[Any,E,Out]] = {
    import quotes.reflect._
    val expr = buildLayerFor[Out](layers)
    '{$expr.asInstanceOf[ZLayer[Any, E, Out]]}

    val graph        = ExprGraph(layers)
    val requirements = intersectionTypes[Out]
    graph.buildLayerFor(requirements)

    val graphString: String = 
      graph.graph
        .map(layer => RenderedGraph(renderExpr(layer)))
        .buildComplete(requirements)
        .toOption
        .get
        .fold(RenderedGraph.Row(List.empty), _ ++ _, _ >>> _).render

    val maxWidth = graphString.maxLineWidth
    val title    = "Layer Graph Visualization"
    val adjust   = (maxWidth - title.length) / 2

    val rendered = "\n" + (" " * adjust) + title.yellow.underlined + "\n\n" + graphString + "\n\n"

    report.throwError(rendered)
  }
}


trait ExprGraphCompileVariants { self : ExprGraph.type =>
  def apply(layers: Expr[Seq[ZLayer[_,_,_]]])(using ctx: Quotes): ExprGraph[LayerExpr] = 
    apply(getNodes(layers))

  def apply(nodes: List[Node[LayerExpr]])(using ctx: Quotes): ExprGraph[LayerExpr] = {
    import ctx.reflect._

    def compileError(message: String) : Nothing = report.throwError(message)
    def empty: LayerExpr = '{ZLayer.succeed(())}
    def composeH(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{$lhs.asInstanceOf[ZLayer[_,_,Has[_]]] +!+ $rhs.asInstanceOf[ZLayer[_,_,Has[_]]]}
    def composeV(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{$lhs.asInstanceOf[ZLayer[_,_,Has[Unit]]] >>> $rhs.asInstanceOf[ZLayer[Has[Unit],_,_]]}

    ExprGraph[LayerExpr](
      Graph(nodes),
      renderExpr,
      compileError,
      empty,
      composeH,
      composeV
      )
  }
}