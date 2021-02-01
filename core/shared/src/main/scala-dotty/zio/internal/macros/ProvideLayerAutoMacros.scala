package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._

import AutoLayerMacroUtils._

object ProvideLayerAutoMacros {
  def provideLayerAutoImpl[R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZIO[Any,E,A]] = {
    val expr = buildLayerFor[R](layers)
    '{$zio.provideLayer($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def provideCustomLayerAutoImpl[R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZIO[ZEnv,E,A]] = {
    val ZEnvRequirements = intersectionTypes[ZEnv]
    val requirements     = intersectionTypes[R] diff ZEnvRequirements

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{ZEnv.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = ExprGraph(nodes).buildLayerFor(requirements)
    '{$zio.provideLayer($expr.asInstanceOf[ZLayer[ZEnv, E, R]])}
  }

  def fromAutoImpl[Out: Type, E: Type](layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZLayer[Any,E,Out]] = {
    val expr = buildLayerFor[Out](layers)
    '{$expr.asInstanceOf[ZLayer[Any, E, Out]]}
  }

  inline def provideLayerAuto[R,E,A](inline zio: ZIO[R,E,A], inline layers: ZLayer[_,E,_]*): ZIO[Any, E, A] = 
    ${provideLayerAutoImpl('zio, 'layers)}
}

object AutoLayerMacroUtils {
  type LayerExpr = Expr[ZLayer[_,_,_]]

  def buildLayerFor[R: Type](layers: Expr[Seq[ZLayer[_,_,_]]])(using Quotes): LayerExpr = {
    val nodes = getNodes(layers)
    ExprGraph(nodes).buildLayerFor(intersectionTypes[R])
  }

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
      tpe.dealias match {
        case AndType(lhs, rhs) => go(lhs.dealias) ++ go(rhs.dealias)
        case AppliedType(_, head :: _) => List(head.dealias)
        case other => List.empty
      }

    go(TypeRepr.of[T]).map(_.show)
  }

  implicit def exprLayerLike(using ctx: Quotes): LayerLike[Expr[ZLayer[_,_,_]]] =
    new LayerLike[LayerExpr] {
      import ctx.reflect._

      override def empty: LayerExpr = '{ZLayer.succeed(())}

      override def composeH(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{${lhs.asExprOf[ZLayer[_,_,Has[_]]]} +!+ ${rhs.asExprOf[ZLayer[_,_,Has[_]]]}}

      override def composeV(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        '{${lhs.asExprOf[ZLayer[_,_,Has[Unit]]]} >>> ${rhs.asExprOf[ZLayer[Has[Unit],_,_]]}}
    }

  implicit def exprExprLike[A](using Quotes): ExprLike[Expr[A]] = new ExprLike[Expr[A]] {
    import quotes.reflect._

    def showTree(expr: Expr[A]): String = expr.asTerm.pos.sourceCode.getOrElse(expr.show)

    def compileError(message: String) : Nothing = report.throwError(message)
  }
}