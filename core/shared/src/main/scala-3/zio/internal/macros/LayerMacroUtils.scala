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
    scala.util.Try(expr.asTerm.pos.sourceCode).toOption.flatten.getOrElse(expr.show)
  }

  def constructLayer[R0: Type, R: Type, E](using ctx: Quotes)(
    layers: Seq[Expr[ZLayer[_, E, _]]],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[R0, E, R]] = {

    import ctx.reflect._

    val targetTypes    = getRequirements[R]
    val remainderTypes = getRequirements[R0]

    val debugMap: PartialFunction[LayerExpr, ZLayer.Debug] =
      ((_: LayerExpr) match {
        case '{zio.ZLayer.Debug.tree}    => Some(ZLayer.Debug.Tree)
        case '{zio.ZLayer.Debug.mermaid} => Some(ZLayer.Debug.Mermaid)
        case _                           => None
      }).unlift


    val builder = LayerBuilder[TypeRepr, LayerExpr](
      target = targetTypes,
      remainder = remainderTypes,
      providedLayers0 = layers.toList,
      debugMap = debugMap,
      typeEquals = _ <:< _,
      foldTree = buildFinalTree,
      method = provideMethod,
      exprToNode = getNode,
      typeToNode = tpe => Node(Nil, List(tpe), tpe.asType match { case '[t] => '{ZLayer.environment[t]} }),
      showExpr = expr => scala.util.Try(expr.asTerm.pos.sourceCode).toOption.flatten.getOrElse(expr.show),
      showType = _.show,
      reportWarn = report.warning(_),
      reportError = report.errorAndAbort(_)
    )

    builder.build.asInstanceOf[Expr[ZLayer[R0, E, R]]]
  }

  def buildFinalTree(tree: LayerTree[LayerExpr])(using ctx: Quotes): LayerExpr = {
    import ctx.reflect._

    val empty: LayerExpr = '{ZLayer.succeed(())}

    def composeH(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
      lhs match {
        case '{$lhs: ZLayer[i, e, o]} =>
          rhs match {
            case '{$rhs: ZLayer[i2, e2, o2]} =>
              '{$lhs.++($rhs)}
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

    val layerExprs: List[LayerExpr] = tree.toList

    ValDef.let(Symbol.spliceOwner, layerExprs.map(_.asTerm)) { idents =>
      val exprMap = layerExprs.zip(idents).toMap

      tree.fold[LayerExpr](
        empty,
        exprMap(_).asExpr.asInstanceOf[LayerExpr],
        composeH,
        composeV
      ).asTerm

    }.asExpr.asInstanceOf[LayerExpr]

  }

  def getNode(layer: LayerExpr)(using ctx: Quotes): Node[ctx.reflect.TypeRepr, LayerExpr] = {
    import quotes.reflect._
    layer match {
      case '{ $layer: ZLayer[in, e, out] } =>
        val inputs = getRequirements[in]
        val outputs = getRequirements[out]
        Node(inputs, outputs, layer)
    }
  }

  // def getRequirements[T: Type](description: String)(using ctx: Quotes): List[ctx.reflect.TypeRepr] = {
  //   import quotes.reflect._

  //   val requirements = intersectionTypes[T]

  //   requirements
  // }

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