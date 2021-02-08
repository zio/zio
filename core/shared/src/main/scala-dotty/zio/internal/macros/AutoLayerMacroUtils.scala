package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import zio.internal.ansi.AnsiStringOps

private [zio] object AutoLayerMacroUtils {
  type LayerExpr = Expr[ZLayer[_,_,_]]

  def renderExpr[A](expr: Expr[A])(using Quotes): String = {
    import quotes.reflect._
    expr.asTerm.pos.sourceCode.getOrElse(expr.show)
  }

  def buildMemoizedLayer(exprGraph: ZLayerExprBuilder[LayerExpr], requirements: List[String])(using  Quotes) : LayerExpr = {
    import quotes.reflect._

    // This is run for its side effects: Reporting compile errors with the original source names.
    val _ = exprGraph.buildLayerFor(requirements)

    val layerExprs = exprGraph.graph.nodes.map(_.value)

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
    buildMemoizedLayer(ZLayerExprBuilder(layers), getRequirements[R])

  def getNodes(layers: Expr[Seq[ZLayer[_,_,_]]])(using Quotes): List[Node[LayerExpr]] = {
    import quotes.reflect._
    layers match {
      case Varargs(args) =>
        args.map {
          case '{$layer: ZLayer[in, e, out]} =>
          val inputs = getRequirements[in]
          val outputs = getRequirements[out]
          Node(inputs, outputs, layer)
        }.toList

      case _ => 
        report.throwError("Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the layers themselves into this method.")
    }
  }

  def getRequirements[T: Type](using Quotes): List[String] = {
      import quotes.reflect._

      val (nonHasTypes, requirements) = intersectionTypes[T].map(_.asType).partitionMap {
        case '[Has[t]] => Right(TypeRepr.of[t].show)
        case '[t] => Left(TypeRepr.of[t].show.white)
      }

    if (nonHasTypes.nonEmpty) report.throwError(s"Contains non-Has types:\n- ${nonHasTypes.mkString("\n- ")}")

    requirements
  }

  def intersectionTypes[T: Type](using ctx: Quotes) : List[ctx.reflect.TypeRepr] = {
    import ctx.reflect._

    def go(tpe: TypeRepr): List[TypeRepr] =
      tpe.dealias.simplified.dealias match {
        case AndType(lhs, rhs) =>
          go(lhs) ++ go(rhs)

        case AppliedType(_, TypeBounds(_,_) :: _) =>
          List.empty

        case other if other =:= TypeRepr.of[Any] =>
          List.empty

        case other if other.dealias != other =>
          go(other)

        case other =>
          List(other.dealias)
      }

    go(TypeRepr.of[T])
  }
}
