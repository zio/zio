package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import zio.internal.ansi.AnsiStringOps

private [zio] object ProviderMacroUtils {
  type ProviderExpr = Expr[ZProvider[_,_,_]]

  def renderExpr[A](expr: Expr[A])(using Quotes): String = {
    import quotes.reflect._
    expr.asTerm.pos.sourceCode.getOrElse(expr.show)
  }

  def buildMemoizedProvider(ctx: Quotes)(exprGraph: ZProviderExprBuilder[ctx.reflect.TypeRepr, ProviderExpr], requirements: List[ctx.reflect.TypeRepr]) : ProviderExpr = {
    import ctx.reflect._

    // This is run for its side effects: Reporting compile errors with the original source names.
    val _ = exprGraph.buildProviderFor(requirements)

    val providerExprs = exprGraph.graph.nodes.map(_.value)

    ValDef.let(Symbol.spliceOwner, providerExprs.map(_.asTerm)) { idents =>
      val exprMap = providerExprs.zip(idents).toMap
      val valGraph = exprGraph.copy( graph =
        exprGraph.graph.map { node =>
          val ident = exprMap(node)
          ident.asExpr.asInstanceOf[ProviderExpr]
        }
      )
      valGraph.buildProviderFor(requirements).asTerm
    }.asExpr.asInstanceOf[ProviderExpr]
  }

  def getNodes(provider: Expr[Seq[ZProvider[_,_,_]]])(using ctx:Quotes): List[Node[ctx.reflect.TypeRepr, ProviderExpr]] = {
    import quotes.reflect._
    provider match {
      case Varargs(provider) =>
        getNodes(provider)

      case other =>
        report.throwError(
          "  ZProvider Wiring Error  ".yellow.inverted + "\n" +
          "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the providers themselves into this method."
        )
    }
  }


  def getNodes(provider: Seq[Expr[ZProvider[_,_,_]]])(using ctx:Quotes): List[Node[ctx.reflect.TypeRepr, ProviderExpr]] = {
    import quotes.reflect._
    provider.map {
      case '{$provider: ZProvider[in, e, out]} =>
      val inputs = getRequirements[in]("Input for " + provider.show.cyan.bold)
      val outputs = getRequirements[out]("Output for " + provider.show.cyan.bold)
      Node(inputs, outputs, provider)
    }.toList
  }

  def getRequirements[T: Type](description: String)(using ctx: Quotes): List[ctx.reflect.TypeRepr] = {
    import quotes.reflect._

    val requirements = intersectionTypes[T]

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
//    ProviderMacros.debugGetRequirements[R]
//  }
//
//  def showTree(any: Any): String = '{
//    ProviderMacros.debugShowTree
//  }
}
