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

    constructTypelessLayer[R0, R, E](layers, provideMethod, false).asExprOf[ZLayer[R0, E, R]]
  }

  def constructStaticSomeLayer[R0: Type, R: Type, E: Type](using Quotes)(
    layers: Seq[LayerExpr[E]],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[R0, E, _]] = {
    import quotes.reflect._

    constructTypelessLayer[R0, R, E](layers, provideMethod, false).asExprOf[ZLayer[R0, E, _]]
  }

  def constructDynamicLayer[R: Type, E: Type](using Quotes)(
    layers: Seq[LayerExpr[E]],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[_, E, R]] = {
    import quotes.reflect._

    constructTypelessLayer[Nothing, R, E](layers, provideMethod, true).asExprOf[ZLayer[_, E, R]]
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

        def composeH(lhs: LayerExpr[E], rhs: LayerExpr[E]): LayerExpr[E] =
          lhs match {
            case '{ $lhs: ZLayer[i, E, o] } =>
              rhs match {
                case '{ $rhs: ZLayer[i2, E, o2] } =>
                  rhs.asTerm match {
                    case _: Ident => '{ $lhs.++($rhs)(summonInline) }
                    case _        => '{ $lhs +!+ $rhs }
                  }
              }
          }

        def composeV(lhs: LayerExpr[E], rhs: LayerExpr[E]): LayerExpr[E] =
          lhs match {
            case '{ $lhs: ZLayer[i, E, o] } =>
              rhs match {
                case '{ $rhs: ZLayer[`o`, E, o2] } =>
                  '{ composeLayer($lhs, $rhs)(using trace) }
              }
          }

        def buildFinalTree(tree: LayerTree[LayerExpr[E]]): LayerExpr[E] = {
          val layerExprs = tree.toList
          ValDef
            .let(Symbol.spliceOwner, layerExprs.map(_.asTerm)) { idents =>
              val exprMap = layerExprs.zip(idents.map(_.asExprOf[ZLayer[_, E, _]])).toMap

              // Bind every shared sub-graph to its own val, so that a sub-graph
              // reachable by many paths through the dependency graph is built as
              // a single ZLayer *instance*. ZLayer's MemoMap is keyed on
              // identity, so this collapses what would otherwise be one copy of
              // the sub-graph per path (see issue #11053).
              val sharedRefs = collection.mutable.Map.empty[Int, Expr[ZLayer[_, E, _]]]

              def fold(t: LayerTree[LayerExpr[E]]): LayerExpr[E] =
                t.foldShared[LayerExpr[E]](
                  '{ ZLayer.unit },
                  exprMap,
                  composeH,
                  composeV,
                  // Sub-trees are pre-bound by `bindAll` below, so by the time
                  // the outer tree is folded every id already has an `Ident`.
                  (id, _) => sharedRefs(id),
                  id => sharedRefs(id)
                )

              // `sharedDefs` lists the sub-graphs in the order they were built,
              // so each body only references ids bound before it. They become
              // nested `ValDef.let`s, so every `Ident` exists before its first
              // use. This is the same dependency ordering the Scala 2 emitter
              // relies on for its `val` definitions.
              def bindAll(ids: List[(Int, LayerTree[LayerExpr[E]])]): Term =
                ids match {
                  case Nil => fold(tree).asTerm
                  case (id, body) :: rest =>
                    ValDef.let(Symbol.spliceOwner, fold(body).asTerm) { ident =>
                      sharedRefs(id) = ident.asExprOf[ZLayer[_, E, _]]
                      bindAll(rest)
                    }
                }

              bindAll(tree.sharedDefs)
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
