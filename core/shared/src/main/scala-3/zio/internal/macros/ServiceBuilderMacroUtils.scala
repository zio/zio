package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import zio.internal.ansi.AnsiStringOps

private [zio] object ServiceBuilderMacroUtils {
  type ServiceBuilderExpr = Expr[ZServiceBuilder[_,_,_]]

  def renderExpr[A](expr: Expr[A])(using Quotes): String = {
    import quotes.reflect._
    expr.asTerm.pos.sourceCode.getOrElse(expr.show)
  }

  def buildMemoizedServiceBuilder(ctx: Quotes)(exprGraph: ZServiceBuilderExprBuilder[ctx.reflect.TypeRepr, ServiceBuilderExpr], requirements: List[ctx.reflect.TypeRepr]) : ServiceBuilderExpr = {
    import ctx.reflect._

    // This is run for its side effects: Reporting compile errors with the original source names.
    val _ = exprGraph.buildServiceBuilderFor(requirements)

    val serviceBuilderExprs = exprGraph.graph.nodes.map(_.value)

    ValDef.let(Symbol.spliceOwner, serviceBuilderExprs.map(_.asTerm)) { idents =>
      val exprMap = serviceBuilderExprs.zip(idents).toMap
      val valGraph = exprGraph.copy( graph =
        exprGraph.graph.map { node =>
          val ident = exprMap(node)
          ident.asExpr.asInstanceOf[ServiceBuilderExpr]
        }
      )
      valGraph.buildServiceBuilderFor(requirements).asTerm
    }.asExpr.asInstanceOf[ServiceBuilderExpr]
  }

  def getNodes(serviceBuilder: Expr[Seq[ZServiceBuilder[_,_,_]]])(using ctx:Quotes): List[Node[ctx.reflect.TypeRepr, ServiceBuilderExpr]] = {
    import quotes.reflect._
    serviceBuilder match {
      case Varargs(serviceBuilder) =>
        getNodes(serviceBuilder)

      case other =>
        report.throwError(
          "  ZServiceBuilder Wiring Error  ".yellow.inverted + "\n" +
          "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the service builders themselves into this method."
        )
    }
  }


  def getNodes(serviceBuilder: Seq[Expr[ZServiceBuilder[_,_,_]]])(using ctx:Quotes): List[Node[ctx.reflect.TypeRepr, ServiceBuilderExpr]] = {
    import quotes.reflect._
    serviceBuilder.map {
      case '{$serviceBuilder: ZServiceBuilder[in, e, out]} =>
      val inputs = getRequirements[in]("Input for " + serviceBuilder.show.cyan.bold)
      val outputs = getRequirements[out]("Output for " + serviceBuilder.show.cyan.bold)
      Node(inputs, outputs, serviceBuilder)
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
//    ServiceBuilderMacros.debugGetRequirements[R]
//  }
//
//  def showTree(any: Any): String = '{
//    ServiceBuilderMacros.debugShowTree
//  }
}
