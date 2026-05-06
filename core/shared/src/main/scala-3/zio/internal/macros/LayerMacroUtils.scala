package zio.internal.macros

import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.ansi.AnsiStringOps
import zio.internal.macros.StringUtils.StringOps
import zio.internal.stacktracer.Tracer

private[zio] object LayerMacroUtils {
  type LayerExpr[E] = Expr[ZLayer[_, E, _]]

  def composeLayer[R1, E, O1, O2](
    lhs: ZLayer[R1, E, O1],
    rhs: ZLayer[O1, E, O2]
  )(using Trace): ZLayer[R1, E, O2] =
    lhs >>> rhs

  def constructStaticLayer[R0: Type, R: Type, E: Type](using Quotes)(
    layers: Seq[LayerExpr[E]],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[R0, E, R]] = {
    import quotes.reflect._

    val typeless = constructTypelessLayer[R0, R, E](layers, provideMethod, false)
      .asExprOf[ZLayer[Any, E, Any]]
    '{ $typeless.asInstanceOf[ZLayer[R0, E, R]] }
  }

  def constructStaticSomeLayer[R0: Type, R: Type, E: Type](using Quotes)(
    layers: Seq[LayerExpr[E]],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[R0, E, _]] = {
    import quotes.reflect._

    val typeless = constructTypelessLayer[R0, R, E](layers, provideMethod, false)
      .asExprOf[ZLayer[Any, E, Any]]
    '{ $typeless.asInstanceOf[ZLayer[R0, E, Any]] }
  }

  def constructDynamicLayer[R: Type, E: Type](using Quotes)(
    layers: Seq[LayerExpr[E]],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[_, E, R]] = {
    import quotes.reflect._

    val typeless = constructTypelessLayer[Nothing, R, E](layers, provideMethod, true)
      .asExprOf[ZLayer[Any, E, Any]]
    '{ $typeless.asInstanceOf[ZLayer[Any, E, R]] }
  }

  private def constructTypelessLayer[R0: Type, R: Type, E: Type](using Quotes)(
    layers: Seq[LayerExpr[E]],
    provideMethod: ProvideMethod,
    inferRemainder: Boolean
  ): Expr[ZLayer[_, _, _]] = {
    import quotes.reflect._

    def renderExpr[A](expr: Expr[A]): String =
      scala.util.Try(expr.asTerm.pos.sourceCode).toOption.flatten.getOrElse(expr.show)

    def getNode(layer: LayerExpr[E]): Node[TypeRepr, LayerExpr[E]] = layer match {
      case '{ $layer: ZLayer[in, e, out] } =>
        val inputs  = getRequirements[in]
        val outputs = getRequirements[out]
        Node(inputs, outputs, layer)
    }

    def getRequirements[T: Type]: List[TypeRepr] = {
      def loop(tpe: TypeRepr): List[TypeRepr] =
        tpe.dealias.simplified match {
          case AndType(lhs, rhs)                          => loop(lhs) ++ loop(rhs)
          case AppliedType(_, TypeBounds(_, _) :: _)      => Nil
          case other if other =:= TypeRepr.of[Any]        => Nil
          case other if other.dealias.simplified != other => loop(other)
          case other                                      => List(other.dealias)
        }

      loop(TypeRepr.of[T])
    }

    val layerToDebug: PartialFunction[LayerExpr[E], ZLayer.Debug] = {
      case '{ ZLayer.Debug.tree }    => ZLayer.Debug.Tree
      case '{ ZLayer.Debug.mermaid } => ZLayer.Debug.Mermaid
    }

    '{
      val trace = summonInline[Trace]

      ${
        def typeToNode(tpe: TypeRepr): Node[TypeRepr, LayerExpr[E]] =
          Node(Nil, List(tpe), tpe.asType match { case '[t] => '{ ZLayer.environment[t](trace) } })

        def rhsOutputType(rhs: LayerExpr[E]): TypeRepr =
          rhs.asTerm.tpe.widen.dealias match {
            case AppliedType(_, List(_, _, out)) => out
            case other =>
              report.errorAndAbort(
                s"Internal layer macro invariant violated: expected ZLayer[_, _, _] for rhs, got ${other.show}"
              )
          }

        def composeH(lhs: LayerExpr[E], rhs: LayerExpr[E]): LayerExpr[E] =
          rhs.asTerm match {
            case _: Ident =>
              rhsOutputType(rhs).asType match {
                case '[o] =>
                  '{
                    $lhs
                      .asInstanceOf[ZLayer[Any, E, Any]]
                      .++[E, Any, Any, o]($rhs.asInstanceOf[ZLayer[Any, E, o]])(summonInline)
                  }
              }
            case _ =>
              '{
                $lhs.asInstanceOf[ZLayer[Any, E, Any]] +!+
                  $rhs.asInstanceOf[ZLayer[Any, E, Any]]
              }
          }

        def composeV(lhs: LayerExpr[E], rhs: LayerExpr[E]): LayerExpr[E] =
          '{
            composeLayer[Any, E, Any, Any](
              $lhs.asInstanceOf[ZLayer[Any, E, Any]],
              $rhs.asInstanceOf[ZLayer[Any, E, Any]]
            )(using trace)
          }

        def buildFinalTree(tree: LayerTree[LayerExpr[E]]): LayerExpr[E] = {
          val layerExprs = tree.toList
          ValDef
            .let(Symbol.spliceOwner, layerExprs.map(_.asTerm)) { idents =>
              val exprMap = layerExprs.zip(idents.map(_.asExprOf[ZLayer[_, E, _]])).toMap
              tree.fold('{ ZLayer.unit }, exprMap, composeH, composeV).asTerm
            }
            .asExprOf[ZLayer[_, E, _]]
        }

        val remainder = if (inferRemainder) {
          RemainderMethod.Inferred
        } else {
          RemainderMethod.Provided(getRequirements[R0])
        }

        val builder = LayerBuilder[TypeRepr, LayerExpr[E]](
          target0 = getRequirements[R],
          remainder = remainder,
          providedLayers0 = layers.toList,
          layerToDebug = layerToDebug,
          typeEquals = _ <:< _,
          sideEffectType = TypeRepr.of[Unit],
          anyType = TypeRepr.of[Any],
          foldTree = buildFinalTree,
          method = provideMethod,
          exprToNode = getNode,
          typeToNode = typeToNode,
          showExpr = renderExpr,
          showType = _.show,
          reportWarn = report.warning,
          reportError = report.errorAndAbort
        )

        builder.build.asTerm.asExprOf[ZLayer[_, _, _]]
      }
    }
  }
}
