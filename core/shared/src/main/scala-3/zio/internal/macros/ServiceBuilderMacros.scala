package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

import ServiceBuilderMacroUtils._

object ServiceBuilderMacros {
  def injectImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], serviceBuilder: Expr[Seq[ZServiceBuilder[_,E,_]]])(using Quotes): Expr[ZIO[R0,E,A]] = {
    val serviceBuilderExpr = fromAutoImpl[R0, R, E](serviceBuilder)
    '{$zio.provideServices($serviceBuilderExpr.asInstanceOf[ZServiceBuilder[R0,E,R]])}
  }

  def fromAutoImpl[R0: Type, R: Type, E: Type](serviceBuilder0: Expr[Seq[ZServiceBuilder[_,E,_]]])(using ctx: Quotes): Expr[ZServiceBuilder[R0,E,R]] = {
    val deferredRequirements = getRequirements[R0]("Specified Remainder")
    val requirements     = getRequirements[R](s"Target Environment")

    val (serviceBuilder, debug) = 
      serviceBuilder0 match {
        case Varargs(serviceBuilder0) =>
          val debug = serviceBuilder0.collectFirst {
              case '{ZServiceBuilder.Debug.tree} => ZServiceBuilder.Debug.Tree
              case '{ZServiceBuilder.Debug.mermaid} => ZServiceBuilder.Debug.Mermaid
          }
          val serviceBuilder = serviceBuilder0.filter {
              case '{ZServiceBuilder.Debug.tree} | '{ZServiceBuilder.Debug.mermaid} => false
              case _ => true
          }
          (serviceBuilder, debug)
      }

    val zEnvServiceBuilder: List[Node[ctx.reflect.TypeRepr, ServiceBuilderExpr]] =
      if (deferredRequirements.nonEmpty) List(Node(List.empty, deferredRequirements, '{ZServiceBuilder.environment[R0]}))
      else List.empty

    val nodes = zEnvServiceBuilder ++ getNodes(serviceBuilder)

    val dep = buildMemoizedServiceBuilder(ctx)(ZServiceBuilderExprBuilder.fromNodes(ctx)(nodes), requirements)
    '{$dep.asInstanceOf[ZServiceBuilder[R0,E,R]] }
  }
}


trait ExprGraphCompileVariants { self : ZServiceBuilderExprBuilder.type =>
  def fromNodes(ctx: Quotes)(nodes: List[Node[ctx.reflect.TypeRepr, ServiceBuilderExpr]]): ZServiceBuilderExprBuilder[ctx.reflect.TypeRepr, ServiceBuilderExpr] = {
    import ctx.reflect._
    implicit val qcx: ctx.type = ctx

    def renderTypeRepr(typeRepr: TypeRepr)(using Quotes): String = {
      import quotes.reflect._
      typeRepr.show
    }

    def compileError(message: String) : Nothing = report.throwError(message)
    def empty: ServiceBuilderExpr = '{ZServiceBuilder.succeed(())}
    def composeH(lhs: ServiceBuilderExpr, rhs: ServiceBuilderExpr): ServiceBuilderExpr = 
      lhs match {
        case '{$lhs: ZServiceBuilder[i, e, o]} => 
          rhs match {
            case '{$rhs: ZServiceBuilder[i2, e2, o2]} => 
              '{$lhs.++($rhs)}
          }
      }

    def composeV(lhs: ServiceBuilderExpr, rhs: ServiceBuilderExpr): ServiceBuilderExpr =
      lhs match {
        case '{$lhs: ZServiceBuilder[i, e, o]} => 
          rhs match {
            case '{$rhs: ZServiceBuilder[i2, e2, o2]} => 
              '{$lhs >>> $rhs.asInstanceOf[ZServiceBuilder[o,e2,o2]]}
          }
      }

    ZServiceBuilderExprBuilder(
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