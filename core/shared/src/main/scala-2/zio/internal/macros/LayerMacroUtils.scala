package zio.internal.macros

import zio._
import zio.internal.TerminalRendering

import scala.reflect.macros.blackbox
import zio.internal.stacktracer.Tracer

private[zio] trait LayerMacroUtils {
  val c: blackbox.Context
  import c.universe._

  type LayerExpr = Expr[ZLayer[_, _, _]]

  private def verifyLayers(layers: Seq[LayerExpr]): Unit =
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

  private def isByName(tpe: Type): Boolean =
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == definitions.ByNameParamClass

  def constructLayer[R0: WeakTypeTag, R: WeakTypeTag, E](
    layers: Seq[LayerExpr],
    provideMethod: ProvideMethod
  ): Expr[ZLayer[R0, E, R]] = {
    verifyLayers(layers)
    val remainderTypes  = getRequirements[R0]
    val targetTypes     = getRequirements[R]
    val debug           = typeOf[ZLayer.Debug.type].termSymbol
    var usesEnvironment = false
    val trace           = c.freshName(TermName("trace"))
    val compose         = c.freshName(TermName("compose"))

    val debugMap: PartialFunction[LayerExpr, ZLayer.Debug] = {
      case Expr(q"$prefix.tree") if prefix.symbol == debug    => ZLayer.Debug.Tree
      case Expr(q"$prefix.mermaid") if prefix.symbol == debug => ZLayer.Debug.Mermaid
    }

    def typeToNode(tpe: Type): Node[Type, LayerExpr] = {
      usesEnvironment = true
      Node(Nil, List(tpe), c.Expr[ZLayer[_, _, _]](q"${reify(ZLayer)}.environment[$tpe]($trace)"))
    }

    def buildFinalTree(tree: LayerTree[LayerExpr]): LayerExpr = {
      val memoList: List[(LayerExpr, LayerExpr)] = tree.toList.map { node =>
        val termName = c.freshName(TermName("layer"))
        node -> c.Expr[ZLayer[_, _, _]](q"$termName")
      }

      val definitions = memoList.map { case (expr, memoizedNode) =>
        q"val ${TermName(memoizedNode.tree.toString)} = $expr"
      }

      var usesCompose = false
      val memoMap     = memoList.toMap
      val layerSym    = typeOf[ZLayer[_, _, _]].typeSymbol
      val layerExpr = tree.fold[LayerExpr](
        z = reify(ZLayer.unit),
        value = memoMap,
        composeH = (lhs, rhs) => c.Expr(q"$lhs ++ $rhs"),
        composeV = (lhs, rhs) => {
          usesCompose = true
          c.Expr(q"$compose($lhs, $rhs)")
        }
      )

      val traceVal = if (usesEnvironment || usesCompose) {
        List(q"val $trace: ${typeOf[Trace]} = ${reify(Tracer)}.newTrace")
      } else {
        Nil
      }

      val composeDef = if (usesCompose) {
        val R  = c.freshName(TypeName("R"))
        val E  = c.freshName(TypeName("E"))
        val O1 = c.freshName(TypeName("O1"))
        val O2 = c.freshName(TypeName("O2"))
        List(q"""
          def $compose[$R, $E, $O1, $O2](lhs: $layerSym[$R, $E, $O1], rhs: $layerSym[$O1, $E, $O2]) =
            lhs.to(rhs)($trace)
        """)
      } else {
        Nil
      }

      c.Expr(q"""
        ..$traceVal
        ..$composeDef
        ..$definitions
        $layerExpr
      """)
    }

    val builder = LayerBuilder[Type, LayerExpr](
      target0 = targetTypes,
      remainder = remainderTypes,
      providedLayers0 = layers.toList,
      layerToDebug = debugMap,
      sideEffectType = definitions.UnitTpe,
      anyType = definitions.AnyTpe,
      typeEquals = _ <:< _,
      foldTree = buildFinalTree,
      method = provideMethod,
      exprToNode = getNode,
      typeToNode = typeToNode,
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      showType = _.toString,
      reportWarn = c.warning(c.enclosingPosition, _),
      reportError = c.abort(c.enclosingPosition, _)
    )

    c.Expr[ZLayer[R0, E, R]](builder.build.tree)
  }

  def provideBaseImpl[F[_, _, _], R0: WeakTypeTag, R: WeakTypeTag, E, A](
    layers: Seq[LayerExpr],
    method: String,
    provideMethod: ProvideMethod
  ): Expr[F[R0, E, A]] = {
    val expr = constructLayer[R0, R, E](layers, provideMethod)
    c.Expr[F[R0, E, A]](q"${c.prefix}.${TermName(method)}($expr)")
  }

  /**
   * Converts a LayerExpr to a Node annotated by the Layer's input and output
   * types.
   */
  def getNode(layer: LayerExpr): Node[Type, LayerExpr] = {
    val typeArgs = layer.actualType.dealias.typeArgs
    // ZIO[in, _, out]
    val in  = typeArgs.head
    val out = typeArgs(2)
    Node(getRequirements(in), getRequirements(out), layer)
  }

  def getRequirements[T: WeakTypeTag]: List[Type] =
    getRequirements(weakTypeOf[T])

  def getRequirements(tpe: Type): List[Type] = {
    val intersectionTypes = tpe.dealias.map(_.dealias).intersectionTypes

    intersectionTypes
      .map(_.dealias)
      .filterNot(t => typeOf[Object] <:< t || typeOf[Any] <:< t)
      .distinct
  }

  def assertProperVarArgs(layer: Seq[Expr[_]]): Unit = {
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
