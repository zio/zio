package zio.internal.macros

import zio._
import zio.internal.TerminalRendering

import scala.reflect.macros.blackbox

private[zio] trait LayerMacroUtils {
  val c: blackbox.Context
  import c.universe._

  type LayerExpr = c.Expr[ZLayer[_, _, _]]

  private def verifyLayers(layers: Seq[c.Expr[ZLayer[_, _, _]]]): Unit =
    for (layer <- layers) {
      layer.tree match {
        case Apply(tree, _) =>
          tree.tpe match {
            case MethodType(params, _) =>
              val methodName          = tree.toString()
              val fullMethodSignature = s"def $methodName${tree.tpe}"

              val byNameParameters =
                params.filter(s => isByName(s.typeSignature)).map(s => s"${s.name}: ${s.typeSignature}")

              if (byNameParameters.nonEmpty) {
                c.abort(
                  NoPosition,
                  TerminalRendering.byNameParameterInMacroError(methodName, fullMethodSignature, byNameParameters)
                )
              }
          }
        case _ => ()
      }
    }

  private def isByName(tpe: c.Type): Boolean =
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == c.universe.definitions.ByNameParamClass

  def constructLayer[R0: c.WeakTypeTag, R: c.WeakTypeTag, E](
    layers: Seq[c.Expr[ZLayer[_, E, _]]],
    provideMethod: ProvideMethod
  ): c.Expr[ZLayer[R0, E, R]] = {

    verifyLayers(layers)
    val remainderTypes = getRequirements[R0]
    val targetTypes    = getRequirements[R]

    val debugMap: PartialFunction[LayerExpr, ZLayer.Debug] =
      scala.Function.unlift((_: LayerExpr).tree match {
        case q"zio.ZLayer.Debug.tree"    => Some(ZLayer.Debug.Tree)
        case q"zio.ZLayer.Debug.mermaid" => Some(ZLayer.Debug.Mermaid)
        case _                           => None
      })

    val builder = LayerBuilder[c.Type, LayerExpr](
      target0 = targetTypes,
      remainder = remainderTypes,
      providedLayers0 = layers.toList,
      layerToDebug = debugMap,
      sideEffectType = c.weakTypeOf[Unit].dealias,
      typeEquals = _ <:< _,
      foldTree = buildFinalTree,
      method = provideMethod,
      exprToNode = getNode,
      typeToNode = tpe => Node(Nil, List(tpe), c.Expr[ZLayer[_, E, _]](q"_root_.zio.ZLayer.environment[$tpe]")),
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      showType = _.toString,
      reportWarn = c.warning(c.enclosingPosition, _),
      reportError = c.abort(c.enclosingPosition, _)
    )

    builder.build.asInstanceOf[c.Expr[ZLayer[R0, E, R]]]
  }

  def provideBaseImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layers: Seq[c.Expr[ZLayer[_, E, _]]],
    method: String,
    provideMethod: ProvideMethod
  ): c.Expr[F[R0, E, A]] = {
    val expr = constructLayer[R0, R, E](layers, provideMethod)
    c.Expr[F[R0, E, A]](q"${c.prefix}.${TermName(method)}(${expr.tree})")
  }

  private def buildFinalTree(tree: LayerTree[LayerExpr]): LayerExpr = {
    val memoMap: Map[LayerExpr, LayerExpr] =
      tree.toList.map { node =>
        val freshName = c.freshName("layer")
        val termName  = TermName(freshName)
        node -> c.Expr(q"$termName")
      }.toMap

    val definitions =
      memoMap.map { case (expr, memoizedNode) =>
        q"val ${TermName(memoizedNode.tree.toString())} = $expr"
      }

    val layerExpr = tree.fold[LayerExpr](
      z = reify(ZLayer.succeed(())),
      value = memoMap(_),
      composeH = (lhs, rhs) => c.Expr(q"""$lhs ++ $rhs"""),
      composeV = (lhs, rhs) => c.Expr(q"""$lhs >>> $rhs""")
    )

    c.Expr(q"""
    ..$definitions
    ${layerExpr.tree}
    """)
  }

  /**
   * Converts a LayerExpr to a Node annotated by the Layer's input and output
   * types.
   */
  def getNode(layer: LayerExpr): Node[c.Type, LayerExpr] = {
    val typeArgs = layer.actualType.dealias.typeArgs
    // ZIO[in, _, out]
    val in  = typeArgs.head
    val out = typeArgs(2)
    Node(getRequirements(in), getRequirements(out), layer)
  }

  def getRequirements[T: c.WeakTypeTag]: List[c.Type] =
    getRequirements(weakTypeOf[T])

  def getRequirements(tpe: Type): List[c.Type] = {
    val intersectionTypes = tpe.dealias.map(_.dealias).intersectionTypes

    intersectionTypes
      .map(_.dealias)
      .filterNot(_.isAny)
      .distinct
  }

  def assertProperVarArgs(layer: Seq[c.Expr[_]]): Unit = {
    val _ = layer.map(_.tree) collect { case Typed(_, Ident(typeNames.WILDCARD_STAR)) =>
      c.abort(
        c.enclosingPosition,
        "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the layers themselves into this method."
      )
    }
  }

  implicit class TypeOps(self: Type) {
    def isAny: Boolean = self.dealias.typeSymbol == typeOf[Any].typeSymbol

    /**
     * Given a type `A with B with C` You'll get back List[A,B,C]
     */
    def intersectionTypes: List[Type] =
      self.dealias match {
        case t: RefinedType =>
          t.parents.flatMap(_.intersectionTypes)
        case TypeRef(_, sym, _) if sym.info.isInstanceOf[RefinedTypeApi] =>
          sym.info.intersectionTypes
        case other =>
          List(other)
      }
  }

}
