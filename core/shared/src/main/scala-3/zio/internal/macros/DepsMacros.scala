package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps

import DepsMacroUtils._

object DepsMacros {
  def injectImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], deps: Expr[Seq[ZDeps[_,E,_]]])(using Quotes): Expr[ZIO[R0,E,A]] = {
    val depsExpr = fromAutoImpl[R0, R, E](deps)
    '{$zio.provideDeps($depsExpr.asInstanceOf[ZDeps[R0,E,R]])}
  }

  def fromAutoImpl[R0: Type, R: Type, E: Type](deps0: Expr[Seq[ZDeps[_,E,_]]])(using ctx: Quotes): Expr[ZDeps[R0,E,R]] = {
    val deferredRequirements = getRequirements[R0]("Specified Remainder")
    val requirements     = getRequirements[R](s"Target Environment")

    val (deps, debug) = 
      deps0 match {
        case Varargs(deps0) =>
          val debug = deps0.collectFirst {
              case '{ZDeps.Debug.tree} => ZDeps.Debug.Tree
              case '{ZDeps.Debug.mermaid} => ZDeps.Debug.Mermaid
          }
          val deps = deps0.filter {
              case '{ZDeps.Debug.tree} | '{ZDeps.Debug.mermaid} => false
              case _ => true
          }
          (deps, debug)
      }

    val zEnvDeps: List[Node[ctx.reflect.TypeRepr, DepsExpr]] =
      if (deferredRequirements.nonEmpty) List(Node(List.empty, deferredRequirements, '{ZDeps.environment[R0]}))
      else List.empty

    val nodes = zEnvDeps ++ getNodes(deps)

    val deps = buildMemoizedDeps(ctx)(ZDepsExprBuilder.fromNodes(ctx)(nodes), requirements)
    '{$deps.asInstanceOf[ZDeps[R0,E,R]] }
  }
}


trait ExprGraphCompileVariants { self : ZDepsExprBuilder.type =>
  def fromNodes(ctx: Quotes)(nodes: List[Node[ctx.reflect.TypeRepr, DepsExpr]]): ZDepsExprBuilder[ctx.reflect.TypeRepr, DepsExpr] = {
    import ctx.reflect._
    implicit val qcx: ctx.type = ctx

    def renderTypeRepr(typeRepr: TypeRepr)(using Quotes): String = {
      import quotes.reflect._
      typeRepr.show
    }

    def compileError(message: String) : Nothing = report.throwError(message)
    def empty: DepsExpr = '{ZDeps.succeed(())}
    def composeH(lhs: DepsExpr, rhs: DepsExpr): DepsExpr = 
      lhs match {
        case '{$lhs: ZDeps[i, e, o]} => 
          rhs match {
            case '{$rhs: ZDeps[i2, e2, o2]} => 
              val has = Expr.summon[Has.Union[o, o2]].get
              val tag = Expr.summon[Tag[o2]].get
              '{$lhs.++($rhs)($has, $tag)}
          }
      }

    def composeV(lhs: DepsExpr, rhs: DepsExpr): DepsExpr =
      lhs match {
        case '{$lhs: ZDeps[i, e, o]} => 
          rhs match {
            case '{$rhs: ZDeps[i2, e2, o2]} => 
              '{$lhs >>> $rhs.asInstanceOf[ZDeps[o,e2,o2]]}
          }
      }

    ZDepsExprBuilder(
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