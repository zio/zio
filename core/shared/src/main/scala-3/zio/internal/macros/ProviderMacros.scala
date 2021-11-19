package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

import ProviderMacroUtils._

object ProviderMacros {
  def injectImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], provider: Expr[Seq[ZProvider[_,E,_]]])(using Quotes): Expr[ZIO[R0,E,A]] = {
    val providerExpr = fromAutoImpl[R0, R, E](provider)
    '{$zio.provide($providerExpr.asInstanceOf[ZProvider[R0,E,R]])}
  }

  def fromAutoImpl[R0: Type, R: Type, E: Type](provider0: Expr[Seq[ZProvider[_,E,_]]])(using ctx: Quotes): Expr[ZProvider[R0,E,R]] = {
    val deferredRequirements = getRequirements[R0]("Specified Remainder")
    val requirements     = getRequirements[R](s"Target Environment")

    val (provider, debug) =
      provider0 match {
        case Varargs(provider0) =>
          val debug = provider0.collectFirst {
              case '{ZProvider.Debug.tree} => ZProvider.Debug.Tree
              case '{ZProvider.Debug.mermaid} => ZProvider.Debug.Mermaid
          }
          val provider = provider0.filter {
              case '{ZProvider.Debug.tree} | '{ZProvider.Debug.mermaid} => false
              case _ => true
          }
          (provider, debug)
      }

    val zEnvProvider: List[Node[ctx.reflect.TypeRepr, ProviderExpr]] =
      if (deferredRequirements.nonEmpty) List(Node(List.empty, deferredRequirements, '{ZProvider.environment[R0]}))
      else List.empty

    val nodes = zEnvProvider ++ getNodes(provider)

    val dep = buildMemoizedProvider(ctx)(ZProviderExprBuilder.fromNodes(ctx)(nodes), requirements)
    '{$dep.asInstanceOf[ZProvider[R0,E,R]] }
  }
}


trait ExprGraphCompileVariants { self : ZProviderExprBuilder.type =>
  def fromNodes(ctx: Quotes)(nodes: List[Node[ctx.reflect.TypeRepr, ProviderExpr]]): ZProviderExprBuilder[ctx.reflect.TypeRepr, ProviderExpr] = {
    import ctx.reflect._
    implicit val qcx: ctx.type = ctx

    def renderTypeRepr(typeRepr: TypeRepr)(using Quotes): String = {
      import quotes.reflect._
      typeRepr.show
    }

    def compileError(message: String) : Nothing = report.throwError(message)
    def empty: ProviderExpr = '{ZProvider.succeed(())}
    def composeH(lhs: ProviderExpr, rhs: ProviderExpr): ProviderExpr =
      lhs match {
        case '{$lhs: ZProvider[i, e, o]} =>
          rhs match {
            case '{$rhs: ZProvider[i2, e2, o2]} =>
              '{$lhs.++($rhs)}
          }
      }

    def composeV(lhs: ProviderExpr, rhs: ProviderExpr): ProviderExpr =
      lhs match {
        case '{$lhs: ZProvider[i, e, o]} =>
          rhs match {
            case '{$rhs: ZProvider[i2, e2, o2]} =>
              '{$lhs >>> $rhs.asInstanceOf[ZProvider[o,e2,o2]]}
          }
      }

    ZProviderExprBuilder(
      Graph(nodes, _ <:< _),
      renderTypeRepr,
      renderExpr,
      compileError,
      empty,
      composeH,
      composeV
      )
  }
}