package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

private [zio] object AutoLayerMacroUtils {
  type LayerExpr = Expr[ZLayer[_,_,_]]

  def renderExpr[A](expr: Expr[A])(using Quotes): String = {
    import quotes.reflect._
    expr.asTerm.pos.sourceCode.getOrElse(expr.show)
  }

  def buildMemoizedLayer(exprGraph: ZLayerExprBuilder[LayerExpr], requirements: List[String])(using ctx: Quotes) : LayerExpr = {
    import ctx.reflect._

    val layerExprs = exprGraph.graph.nodes.map(_.value)

    // This is run for its side effects: Reporting compile errors with the original source names.
    val _ = exprGraph.buildLayerFor(requirements)

    ValDef.let(Symbol.spliceOwner, layerExprs.map(_.asTerm)) { idents =>
      val exprMap = layerExprs.zip(idents).toMap
      val valGraph = exprGraph.copy( graph =
        exprGraph.graph.map { node =>
          val ident = exprMap(node)
          ident.asExpr.asInstanceOf[LayerExpr]
        }
      )
      valGraph.buildLayerFor(requirements).asTerm
    }.asExpr.asInstanceOf[LayerExpr]
  }

  def buildLayerFor[R: Type](layers: Expr[Seq[ZLayer[_,_,_]]])(using Quotes): LayerExpr =
    buildMemoizedLayer(ExprGraph(layers), intersectionTypes[R])

  def getNodes(layers: Expr[Seq[ZLayer[_,_,_]]])(using Quotes): List[Node[LayerExpr]] =
    layers match {
      case Varargs(args) =>
        args.map {
          case '{$layer: ZLayer[in, e, out]} =>
          val inputs = intersectionTypes[in]
          val outputs = intersectionTypes[out]
          Node(inputs, outputs, layer)
        }.toList
    }


  def intersectionTypes[T: Type](using ctx: Quotes) : List[String] = {
    import ctx.reflect._

    def go(tpe: TypeRepr): List[TypeRepr] =
      tpe.dealias.simplified.dealias match {
        case AndType(lhs, rhs) =>
          go(lhs) ++ go(rhs)

        case AppliedType(_, TypeBounds(_,_) :: _) =>
          List.empty

        case AppliedType(h, head :: Nil) if head.dealias =:= head =>
          List(head.dealias)

        case AppliedType(h, head :: t) =>
          go(head) ++ t.flatMap(t => go(t))

        case other if other =:= TypeRepr.of[Any] =>
          List.empty

        case other if other.dealias != other =>
          go(other)

        case other =>
          List(other.dealias)
      }

    go(TypeRepr.of[T]).map(_.show)
  }
}
