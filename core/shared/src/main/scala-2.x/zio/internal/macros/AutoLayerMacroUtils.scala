package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

import scala.reflect.macros.blackbox

private[zio] trait AutoLayerMacroUtils {
  val c: blackbox.Context
  import c.universe._

  type LayerExpr = c.Expr[ZLayer[_, _, _]]

  def generateExprGraph(layers: Seq[LayerExpr]): ZLayerExprBuilder[LayerExpr] =
    generateExprGraph(layers.map(getNode).toList)

  def generateExprGraph(nodes: List[Node[LayerExpr]]): ZLayerExprBuilder[LayerExpr] =
    ZLayerExprBuilder[LayerExpr](
      graph = Graph(nodes),
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      abort = c.abort(c.enclosingPosition, _),
      emptyExpr = reify(ZLayer.succeed(())),
      composeH = (lhs, rhs) => c.Expr(q"""$lhs +!+ $rhs"""),
      composeV = (lhs, rhs) => c.Expr(q"""$lhs >>> $rhs""")
    )

  def buildMemoizedLayer(exprGraph: ZLayerExprBuilder[LayerExpr], requirements: List[String]): LayerExpr = {
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
    val layerExpr = exprGraph.copy(graph = Graph(memoizedNodes)).buildLayerFor(requirements)

    c.Expr(q"""
    ..$definitions
    ${layerExpr.tree}
    """)
  }

  def getNode(layer: LayerExpr): Node[LayerExpr] = {
    val tpe                   = layer.actualType.dealias
    val in :: _ :: out :: Nil = tpe.typeArgs
    Node(getRequirements(in), getRequirements(out), layer)
  }

  def getRequirements[T: c.WeakTypeTag]: List[String] =
    getRequirements(weakTypeOf[T])

  def isValidHasType(tpe: Type): Boolean =
    tpe.isHas || tpe.isAny

  def getRequirements(tpe: Type): List[String] = {
    val intersectionTypes = tpe.intersectionTypes

    intersectionTypes.filter(!isValidHasType(_)) match {
      case Nil => ()
      case nonHasTypes =>
        c.abort(
          c.enclosingPosition,
          s"\nContains non-Has types:\n- ${nonHasTypes.map(_.toString.white).mkString("\n- ")}"
        )
    }

    intersectionTypes
      .filter(_.isHas)
      .map(_.dealias.typeArgs.head.dealias.map(_.dealias).toString)
      .distinct
  }

  def assertProperVarArgs(layers: Seq[c.Expr[_]]): Unit =
    layers.map(_.tree) collect { case Typed(_, Ident(typeNames.WILDCARD_STAR)) =>
      c.abort(
        c.enclosingPosition,
        "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the layers themselves into this method."
      )
    }

  implicit class TypeOps(self: Type) {
    def isHas: Boolean = self.dealias.typeSymbol == typeOf[Has[_]].typeSymbol

    def isAny: Boolean = self.dealias.typeSymbol == typeOf[Any].typeSymbol

    /**
     * Given a type `A with B with C` You'll get back List[A,B,C]
     */
    def intersectionTypes: List[Type] =
      self.dealias.map(_.dealias) match {
        case t: RefinedType =>
          t.parents.flatMap(_.intersectionTypes)
        case TypeRef(_, sym, _) if sym.info.isInstanceOf[RefinedTypeApi] =>
          sym.info.intersectionTypes
        case other =>
          List(other)
      }
  }

  implicit class ZLayerExprOps(self: c.Expr[ZLayer[_, _, _]]) {
    def outputTypes: List[Type] = self.actualType.dealias.typeArgs(2).intersectionTypes
    def inputTypes: List[Type]  = self.actualType.dealias.typeArgs.head.intersectionTypes
  }

  implicit class TreeOps(self: c.Expr[_]) {
    def showTree: String = CleanCodePrinter.show(c)(self.tree)
  }
}

trait ExprGraphCompileVariants {}
