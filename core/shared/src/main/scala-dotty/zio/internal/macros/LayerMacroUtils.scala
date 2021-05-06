package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import zio.internal.ansi.AnsiStringOps

private [zio] object LayerMacroUtils {
  type LayerExpr = Expr[ZLayer[_,_,_]]

  def renderExpr[A](expr: Expr[A])(using Quotes): String = {
    import quotes.reflect._
    expr.asTerm.pos.sourceCode.getOrElse(expr.show)
  }

  def buildMemoizedLayer(ctx: Quotes)(exprGraph: ZLayerExprBuilder[ctx.reflect.TypeRepr, LayerExpr], requirements: List[ctx.reflect.TypeRepr]) : LayerExpr = {
    import ctx.reflect._

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

  def buildLayerFor[R: Type](layers: Expr[Seq[ZLayer[_,_,_]]])(using ctx: Quotes): LayerExpr = {
    import quotes.reflect._
    buildMemoizedLayer(ctx)(ZLayerExprBuilder(ctx)(layers), getRequirements[R](TypeRepr.of[R].show))
  }

  def getNodes(layers: Expr[Seq[ZLayer[_,_,_]]])(using ctx:Quotes): List[Node[ctx.reflect.TypeRepr, LayerExpr]] = {
    import quotes.reflect._
    layers match {
      case Varargs(args) =>
        args.map {
          case '{$layer: ZLayer[in, e, out]} =>
          val inputs = getRequirements[in]("Input for " + layer.show.cyan.bold)
          val outputs = getRequirements[out]("Output for " + layer.show.cyan.bold)
          Node(inputs, outputs, layer)
        }.toList

      case other =>
        report.throwError(
          "  ZLayer Wiring Error  ".yellow.inverted + "\n" +
          "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the layers themselves into this method."
        )
    }
  }

  def getRequirements[T: Type](description: String)(using ctx: Quotes): List[ctx.reflect.TypeRepr] = {
      import quotes.reflect._

      val (nonHasTypes, requirements) = intersectionTypes[T].map(_.asType).partitionMap {
        case '[Has[t]] => Right(TypeRepr.of[t])
        case '[t] => Left(TypeRepr.of[t])
      }

    if (nonHasTypes.nonEmpty) report.throwError(
      "  ZLayer Wiring Error  ".yellow.inverted + "\n" +
      s"${description} contains non-Has types:\n- ${nonHasTypes.mkString("\n- ")}"
    )

    requirements
  }

  def intersectionTypes[T: Type](using ctx: Quotes) : List[ctx.reflect.TypeRepr] = {
    import ctx.reflect._

    def go(tpe: TypeRepr): List[TypeRepr] =
      tpe.dealias.simplified match {
        case AndType(lhs, rhs) =>
          go(lhs) ++ go(rhs)

        case AppliedType(_, TypeBounds(_,_) :: _) =>
          List.empty

        case other if other =:= TypeRepr.of[Any] =>
          List.empty

        case other if other.dealias.simplified != other =>
          go(other)

        case other =>
          List(other.dealias)
      }

    go(TypeRepr.of[T])
  }
}

private[zio] object MacroUnitTestUtils {
//  def getRequirements[R]: List[String] = '{
//    LayerMacros.debugGetRequirements[R]
//  }
//
//  def showTree(any: Any): String = '{
//    LayerMacros.debugShowTree
//  }
}
