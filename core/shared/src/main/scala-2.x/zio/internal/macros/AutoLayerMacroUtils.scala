package zio.internal.macros

import zio._

import scala.reflect.macros.blackbox

trait AutoLayerMacroUtils {
  val c: blackbox.Context
  import c.universe._

  private val zioSymbol = typeOf[Has[_]].typeSymbol

  type LayerExpr = c.Expr[ZLayer[_, _, _]]

  def generateExprGraph(layers: Seq[LayerExpr]): ExprGraph[LayerExpr] =
    generateExprGraph(layers.map(getNode).toList)

  def generateExprGraph(nodes: List[Node[LayerExpr]]): ExprGraph[LayerExpr] =
    ExprGraph[LayerExpr](
      graph = Graph(nodes),
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      abort = c.abort(c.enclosingPosition, _),
      emptyExpr = reify(ZLayer.succeed(())),
      composeH = (lhs, rhs) => c.Expr(q"""$lhs +!+ $rhs"""),
      composeV = (lhs, rhs) => c.Expr(q"""$lhs >>> $rhs""")
    )

  def getNode(layer: LayerExpr): Node[LayerExpr] = {
    val tpe                   = layer.actualType.dealias
    val in :: _ :: out :: Nil = tpe.typeArgs
    Node(getRequirements(in), getRequirements(out), layer)
  }

  def getRequirements[T: c.WeakTypeTag]: List[String] =
    getRequirements(weakTypeOf[T])

  def getRequirements(tpe: Type): List[String] =
    tpe.intersectionTypes
      .filter(_.dealias.typeSymbol == zioSymbol)
      .map(_.dealias.typeArgs.head.dealias.toString)
      .distinct

  def assertProperVarArgs(layers: Seq[c.Expr[_]]): Unit =
    layers.map(_.tree) collect { case Typed(_, Ident(typeNames.WILDCARD_STAR)) =>
      c.abort(
        c.enclosingPosition,
        "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the layers themselves into this method."
      )
    }

  implicit class TypeOps(self: Type) {

    /**
     * Given a type `A with B with C` You'll get back List[A,B,C]
     */
    def intersectionTypes: List[Type] = self.dealias match {
      case t: RefinedType =>
        t.parents.flatMap(_.dealias.intersectionTypes)
      case _ => List(self)
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
