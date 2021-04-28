package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

import LayerMacroUtils._

object LayerMacros {
  def injectImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZIO[R0,E,A]] = {
    val layerExpr = fromAutoImpl[R0, R, E](layers)
    '{$zio.provideLayerManual($layerExpr.asInstanceOf[ZLayer[R0,E,R]])}
  }

  def fromAutoImpl[In: Type, Out: Type, E: Type](layers: Expr[Seq[ZLayer[_,E,_]]])(using ctx: Quotes): Expr[ZLayer[In,E,Out]] = {
    val deferredRequirements = getRequirements[In]("Specified Remainder")
    val requirements     = getRequirements[Out](s"Target Environment")

    val zEnvLayer: List[Node[ctx.reflect.TypeRepr, LayerExpr]] =
      if (deferredRequirements.nonEmpty)
        List(Node(List.empty, deferredRequirements, '{ZLayer.requires[In]}))
      else
        List.empty

    val nodes = zEnvLayer ++ getNodes(layers)

    buildMemoizedLayer(ctx)(ZLayerExprBuilder.fromNodes(ctx)(nodes), requirements)
      .asInstanceOf[Expr[ZLayer[In,E,Out]]]
  }
}


trait ExprGraphCompileVariants { self : ZLayerExprBuilder.type =>
  def apply(ctx: Quotes)(layers: Expr[Seq[ZLayer[_,_,_]]]): ZLayerExprBuilder[ctx.reflect.TypeRepr, LayerExpr] =  {
    implicit val qcx: ctx.type = ctx
    fromNodes(ctx)(getNodes(layers))
  }

  def fromNodes(ctx: Quotes)(nodes: List[Node[ctx.reflect.TypeRepr, LayerExpr]]): ZLayerExprBuilder[ctx.reflect.TypeRepr, LayerExpr] = {
    import ctx.reflect._
    implicit val qcx: ctx.type = ctx

    def renderTypeRepr(typeRepr: TypeRepr)(using Quotes): String = {
      import quotes.reflect._
      typeRepr.show
    }

    def compileError(message: String) : Nothing = report.throwError(message)
    def empty: LayerExpr = '{ZLayer.succeed(())}
    def composeH(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{$lhs.asInstanceOf[ZLayer[_,_,Has[_]]] +!+ $rhs.asInstanceOf[ZLayer[_,_,Has[_]]]}
    def composeV(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{$lhs.asInstanceOf[ZLayer[_,_,Has[Unit]]] >>> $rhs.asInstanceOf[ZLayer[Has[Unit],_,_]]}

    ZLayerExprBuilder(
      Graph(nodes, _ =:= _),
      renderTypeRepr,
      renderExpr,
      compileError,
      empty,
      composeH,
      composeV
      )
  }
}