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
    '{$zio.provideLayer($layerExpr.asInstanceOf[ZLayer[R0,E,R]])}
  }

  def fromAutoImpl[R0: Type, R: Type, E: Type](layers0: Expr[Seq[ZLayer[_,E,_]]])(using ctx: Quotes): Expr[ZLayer[R0,E,R]] = {
    val deferredRequirements = getRequirements[R0]("Specified Remainder")
    val requirements     = getRequirements[R](s"Target Environment")

    val (layers, debug) = 
      layers0 match {
        case Varargs(layers0) =>
          val debug = layers0.collectFirst {
              case '{ZLayer.Debug.tree} => ZLayer.Debug.Tree
              case '{ZLayer.Debug.mermaid} => ZLayer.Debug.Mermaid
          }
          val layers = layers0.filter {
              case '{ZLayer.Debug.tree} | '{ZLayer.Debug.mermaid} => false
              case _ => true
          }
          (layers, debug)
      }

    val zEnvLayer: List[Node[ctx.reflect.TypeRepr, LayerExpr]] =
      if (deferredRequirements.nonEmpty) List(Node(List.empty, deferredRequirements, '{ZLayer.environment[R0]}))
      else List.empty

    val nodes = zEnvLayer ++ getNodes(layers)

    val layer = buildMemoizedLayer(ctx)(ZLayerExprBuilder.fromNodes(ctx)(nodes), requirements)
    '{$layer.asInstanceOf[ZLayer[R0,E,R]] }
  }
}


trait ExprGraphCompileVariants { self : ZLayerExprBuilder.type =>
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
      lhs match {
        case '{$lhs: ZLayer[i, e, o]} => 
          rhs match {
            case '{$rhs: ZLayer[i2, e2, o2]} => 
              val has = Expr.summon[Has.Union[o, o2]].get
              val tag = Expr.summon[Tag[o2]].get
              '{$lhs.++($rhs)($has, $tag)}
          }
      }

    def composeV(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
      lhs match {
        case '{$lhs: ZLayer[i, e, o]} => 
          rhs match {
            case '{$rhs: ZLayer[i2, e2, o2]} => 
              '{$lhs >>> $rhs.asInstanceOf[ZLayer[o,e2,o2]]}
          }
      }

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