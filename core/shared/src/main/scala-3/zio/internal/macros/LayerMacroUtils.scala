package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import zio.internal.ansi.AnsiStringOps

private [zio] object LayerMacroUtils {
  type LayerExpr[E] = Expr[ZLayer[_,E,_]]

  def renderExpr[A](expr: Expr[A])(using Quotes): String = {
    import quotes.reflect._
    scala.util.Try(expr.asTerm.pos.sourceCode).toOption.flatten.getOrElse(expr.show)
  }

  def constructLayer[R0: Type, R: Type, E: Type](using ctx: Quotes)(
    layers: Seq[Expr[ZLayer[_, E, _]]],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[R0, E, R]] = {
    import ctx.reflect._

    val targetTypes    = getRequirements[R]
    val remainderTypes = getRequirements[R0]

    val layerToDebug: PartialFunction[LayerExpr[E], ZLayer.Debug] =
      ((_: LayerExpr[E]) match {
        case '{zio.ZLayer.Debug.tree}    => Some(ZLayer.Debug.Tree)
        case '{zio.ZLayer.Debug.mermaid} => Some(ZLayer.Debug.Mermaid)
        case _                           => None
      }).unlift

    val builder = LayerBuilder[TypeRepr, LayerExpr[E]](
      target0 = targetTypes,
      remainder = remainderTypes,
      providedLayers0 = layers.toList,
      layerToDebug = layerToDebug,
      typeEquals = _ <:< _,
      sideEffectType = TypeRepr.of[Unit],
      foldTree = buildFinalTree,
      method = provideMethod,
      exprToNode = getNode,
      typeToNode = tpe => Node(Nil, List(tpe), tpe.asType match { case '[t] => '{ZLayer.environment[t] } }),
      showExpr = expr => scala.util.Try(expr.asTerm.pos.sourceCode).toOption.flatten.getOrElse(expr.show),
      showType = _.show,
      reportWarn = report.warning(_),
      reportError = report.errorAndAbort(_)
    )

    builder.build.asInstanceOf[Expr[ZLayer[R0, E, R]]]
  }

  def buildFinalTree[E: Type](tree: LayerTree[LayerExpr[E]])(using ctx: Quotes): LayerExpr[E] = {
    import ctx.reflect._

    val empty: LayerExpr[E] = '{ZLayer.succeed(())}

    def composeH(lhs: LayerExpr[E], rhs: LayerExpr[E]): LayerExpr[E] =
      lhs match {
        case '{$lhs: ZLayer[i, e, o]} =>
          rhs match {
            case '{$rhs: ZLayer[i2, e2, o2]} =>
              '{$lhs.++($rhs)}
          }
      }

    def composeV(lhs: LayerExpr[E], rhs: LayerExpr[E]): LayerExpr[E] =
      lhs match {
        case '{$lhs: ZLayer[i, E, o]} =>
          rhs match {
            case '{$rhs: ZLayer[`o`, E, o2]} =>
              '{$lhs to $rhs}
          }
      }

    val layerExprs: List[LayerExpr[E]] = tree.toList

    ValDef.let(Symbol.spliceOwner, layerExprs.map(_.asTerm)) { idents =>
      val exprMap = layerExprs.zip(idents).toMap

      tree.fold[LayerExpr[E]](
        empty,
        exprMap(_).asExpr.asInstanceOf[LayerExpr[E]],
        composeH,
        composeV
      ).asTerm

    }.asExpr.asInstanceOf[LayerExpr[E]]

  }

  def getNode[E: Type](layer: LayerExpr[E])(using ctx: Quotes): Node[ctx.reflect.TypeRepr, LayerExpr[E]] = {
    import quotes.reflect._
    layer match {
      case '{ $layer: ZLayer[in, e, out] } =>
        val inputs = getRequirements[in]
        val outputs = getRequirements[out]
        Node(inputs, outputs, layer)
    }
  }

  def getRequirements[T: Type](using ctx: Quotes) : List[ctx.reflect.TypeRepr] = {
    import ctx.reflect._

    def loop(tpe: TypeRepr): List[TypeRepr] =
      tpe.dealias.simplified match {
        case AndType(lhs, rhs) =>
          loop(lhs) ++ loop(rhs)

        case AppliedType(_, TypeBounds(_,_) :: _) =>
          List.empty

        case other if other =:= TypeRepr.of[Any] =>
          List.empty

        case other if other.dealias.simplified != other =>
          loop(other)

        case other =>
          List(other.dealias)
      }

    loop(TypeRepr.of[T])
  }
}