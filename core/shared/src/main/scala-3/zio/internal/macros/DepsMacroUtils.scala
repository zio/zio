package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import zio.internal.ansi.AnsiStringOps

private [zio] object DepsMacroUtils {
  type DepsExpr = Expr[ZDeps[_,_,_]]

  def renderExpr[A](expr: Expr[A])(using Quotes): String = {
    import quotes.reflect._
    expr.asTerm.pos.sourceCode.getOrElse(expr.show)
  }

  def buildMemoizedDeps(ctx: Quotes)(exprGraph: ZDepsExprBuilder[ctx.reflect.TypeRepr, DepsExpr], requirements: List[ctx.reflect.TypeRepr]) : DepsExpr = {
    import ctx.reflect._

    // This is run for its side effects: Reporting compile errors with the original source names.
    val _ = exprGraph.buildDepsFor(requirements)

    val depsExprs = exprGraph.graph.nodes.map(_.value)

    ValDef.let(Symbol.spliceOwner, depsExprs.map(_.asTerm)) { idents =>
      val exprMap = depsExprs.zip(idents).toMap
      val valGraph = exprGraph.copy( graph =
        exprGraph.graph.map { node =>
          val ident = exprMap(node)
          ident.asExpr.asInstanceOf[DepsExpr]
        }
      )
      valGraph.buildDepsFor(requirements).asTerm
    }.asExpr.asInstanceOf[DepsExpr]
  }

  def getNodes(deps: Expr[Seq[ZDeps[_,_,_]]])(using ctx:Quotes): List[Node[ctx.reflect.TypeRepr, DepsExpr]] = {
    import quotes.reflect._
    deps match {
      case Varargs(deps) =>
        getNodes(deps)

      case other =>
        report.throwError(
          "  ZDeps Wiring Error  ".yellow.inverted + "\n" +
          "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the dependencies themselves into this method."
        )
    }
  }


  def getNodes(deps: Seq[Expr[ZDeps[_,_,_]]])(using ctx:Quotes): List[Node[ctx.reflect.TypeRepr, DepsExpr]] = {
    import quotes.reflect._
    deps.map {
      case '{$deps: ZDeps[in, e, out]} =>
      val inputs = getRequirements[in]("Input for " + deps.show.cyan.bold)
      val outputs = getRequirements[out]("Output for " + deps.show.cyan.bold)
      Node(inputs, outputs, deps)
    }.toList
  }

  def getRequirements[T: Type](description: String)(using ctx: Quotes): List[ctx.reflect.TypeRepr] = {
      import quotes.reflect._

      val (nonHasTypes, requirements) = intersectionTypes[T].map(_.asType).partitionMap {
        case '[Has[t]] => Right(TypeRepr.of[t])
        case '[t] => Left(TypeRepr.of[t])
      }

    if (nonHasTypes.nonEmpty) report.throwError(
      "  ZDeps Wiring Error  ".yellow.inverted + "\n" +
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
//    DepsMacros.debugGetRequirements[R]
//  }
//
//  def showTree(any: Any): String = '{
//    DepsMacros.debugShowTree
//  }
}
