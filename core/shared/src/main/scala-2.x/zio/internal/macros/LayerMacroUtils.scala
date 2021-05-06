package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

import scala.reflect.macros.blackbox

private[zio] trait LayerMacroUtils {
  val c: blackbox.Context
  import c.universe._

  type LayerExpr = c.Expr[ZLayer[_, _, _]]

  def generateExprGraph(layers: Seq[LayerExpr]): ZLayerExprBuilder[c.Type, LayerExpr] =
    generateExprGraph(layers.map(getNode).toList)

  def generateExprGraph(nodes: List[Node[c.Type, LayerExpr]]): ZLayerExprBuilder[c.Type, LayerExpr] =
    ZLayerExprBuilder[c.Type, LayerExpr](
      graph = Graph(
        nodes = nodes,
        // The types must be `.toString`-ed as a backup in the case of refinement
        // types.
        keyEquals = (t1, t2) => t1 =:= t2 || (t1.toString == t2.toString)
      ),
      showKey = tpe => tpe.toString,
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      abort = c.abort(c.enclosingPosition, _),
      emptyExpr = reify(ZLayer.succeed(())),
      composeH = (lhs, rhs) => c.Expr(q"""$lhs +!+ $rhs"""),
      composeV = (lhs, rhs) => c.Expr(q"""$lhs >>> $rhs""")
    )

  def buildSomeMemoizedLayer[R0: c.WeakTypeTag, R: c.WeakTypeTag, E](
    layers: Seq[c.Expr[ZLayer[_, E, _]]]
  ): c.Expr[ZLayer[R0, E, R]] = {
    assertEnvIsNotNothing[R]()
    assertProperVarArgs(layers)

    val deferredRequirements = getRequirements[R0]

    val deferredLayer =
      if (deferredRequirements.nonEmpty) List(Node(List.empty, deferredRequirements, reify(ZLayer.requires[R0])))
      else Nil

    val nodes     = deferredLayer ++ layers.map(getNode)
    val exprGraph = generateExprGraph(nodes)

    buildMemoizedLayer(exprGraph, getRequirements[R]).asInstanceOf[c.Expr[ZLayer[R0, E, R]]]
  }

  def buildMemoizedLayer(exprGraph: ZLayerExprBuilder[c.Type, LayerExpr], requirements: List[c.Type]): LayerExpr = {
    // This is run for its side effects: Reporting compile errors with the original source names.
    val _ = exprGraph.buildLayerFor(requirements)

    val nodes = exprGraph.graph.nodes
    val memoizedNodes = nodes.map { node =>
      val freshName = c.freshName("layer")
      val termName  = TermName(freshName)
      node.copy(value = c.Expr[ZLayer[_, _, _]](q"$termName"))
    }

    val definitions = memoizedNodes.zip(nodes).map { case (memoizedNode, node) =>
      ValDef(Modifiers(), TermName(memoizedNode.value.tree.toString()), TypeTree(), node.value.tree)
    }
    val layerExpr = exprGraph
      .copy(graph = Graph[c.Type, LayerExpr](memoizedNodes, exprGraph.graph.keyEquals))
      .buildLayerFor(requirements)

    c.Expr(q"""
    ..$definitions
    ${layerExpr.tree}
    """)
  }

  /**
   * Ensures the macro has been annotated with the intended result type.
   * The macro will not behave correctly otherwise.
   */
  def assertEnvIsNotNothing[Out: c.WeakTypeTag](): Unit = {
    val outType     = weakTypeOf[Out]
    val nothingType = weakTypeOf[Nothing]
    if (outType == nothingType) {
      val errorMessage =
        s"""
${"  ZLayer Wiring Error  ".red.bold.inverted}

You must provide a type to ${"wire".cyan.bold} (e.g. ${"ZLayer.wire".cyan.bold}${"[A with B]".cyan.bold.underlined}${"(A.live, B.live)".cyan.bold})

"""
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

  def getNode(layer: LayerExpr): Node[c.Type, LayerExpr] = {
    val tpeArgs = layer.actualType.dealias.typeArgs
    // ZLayer[in, _, out]
    val in  = tpeArgs.head
    val out = tpeArgs(2)
    Node(getRequirements(in), getRequirements(out), layer)
  }

  def getRequirements[T: c.WeakTypeTag]: List[c.Type] =
    getRequirements(weakTypeOf[T])

  def isValidHasType(tpe: Type): Boolean =
    tpe.isHas || tpe.isAny

  def getRequirements(tpe: Type): List[c.Type] = {
    val intersectionTypes = tpe.intersectionTypes

    intersectionTypes.filter(!isValidHasType(_)) match {
      case Nil => ()
      case nonHasTypes =>
        c.abort(
          c.enclosingPosition,
          s"\nContains non-Has types:\n- ${nonHasTypes.map(_.toString.cyan.bold).mkString("\n- ")}"
        )
    }

    intersectionTypes
      .filter(_.isHas)
      .map(_.dealias.typeArgs.head)
      .distinct
  }

  def assertProperVarArgs(layers: Seq[c.Expr[_]]): Unit = {
    val _ = layers.map(_.tree) collect { case Typed(_, Ident(typeNames.WILDCARD_STAR)) =>
      c.abort(
        c.enclosingPosition,
        "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the layers themselves into this method."
      )
    }
  }

  implicit class TypeOps(self: Type) {
    def isHas: Boolean = self.dealias.typeSymbol == typeOf[Has[_]].typeSymbol

    def isAny: Boolean = self.dealias.typeSymbol == typeOf[Any].typeSymbol

    /**
     * Given a type `A with B with C`, you'll get back `List(A,B,C)`
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

  implicit class TreeOps(self: c.Expr[_]) {
    def showTree: String = CleanCodePrinter.show(c)(self.tree)
  }
}

trait ExprGraphCompileVariants {}
