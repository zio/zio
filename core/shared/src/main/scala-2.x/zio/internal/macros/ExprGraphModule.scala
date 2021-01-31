package zio.internal.macros

import zio.{Chunk, NonEmptyChunk}

import scala.reflect.macros.blackbox

// TODO: Replace these no-ops with an actual implementation.
object ansi {
  def Red(string: String): String           = string
  def RedUnderlined(string: String): String = string

  def White(string: String): String           = string
  def WhiteUnderlined(string: String): String = string

  def Bold(string: String): String    = string
  def Magenta(string: String): String = string
}

private[zio] trait ExprGraphModule { self: MacroUtils =>
  val c: blackbox.Context
  import c.universe._

  sealed case class ExprGraph(graph: Graph[LayerExpr]) {
    def buildLayerFor(output: List[String]): LayerExpr =
      if (output.isEmpty) {
        reify(zio.ZLayer.succeed(())).asInstanceOf[LayerExpr]
      } else
        graph.buildComplete(output) match {
          case Left(errors) =>
            c.abort(c.enclosingPosition, renderErrors(errors))
          case Right(value) =>
            value
        }

    private def renderErrors(errors: ::[GraphError[LayerExpr]]): String = {
      val allErrors = sortErrors(errors)

      val errorMessage =
        allErrors
          .map(renderError)
          .mkString("\n")
          .linesIterator
          .mkString("\n🪄  ")
      val title = ansi.RedUnderlined("ZLayer Auto-Build Issue")
      s"""
🪄  $title
🪄  $errorMessage

"""
    }

    /**
     * Return only the first level of circular dependencies, as these will be the most relevant.
     */
    private def sortErrors(errors: ::[GraphError[LayerExpr]]): Chunk[GraphError[LayerExpr]] = {
      val (circularDependencyErrors, otherErrors) =
        NonEmptyChunk.fromIterable(errors.head, errors.tail).distinct.partitionMap {
          case circularDependency: GraphError.CircularDependency[LayerExpr] =>
            Left(circularDependency)
          case other => Right(other)
        }
      val sorted                    = circularDependencyErrors.sortBy(_.depth)
      val lowestDepthCircularErrors = sorted.takeWhile(_.depth == sorted.headOption.map(_.depth).getOrElse(0))
      lowestDepthCircularErrors ++ otherErrors
    }

    private def renderError(error: GraphError[LayerExpr]): String =
      error match {
        case GraphError.MissingDependency(node, dependency) =>
          val styledDependency = ansi.WhiteUnderlined(dependency)
          val styledLayer      = ansi.White(node.value.showTree)
          s"""
provide $styledDependency
    for $styledLayer"""

        case GraphError.MissingTopLevelDependency(dependency) =>
          val styledDependency = ansi.WhiteUnderlined(dependency)
          s"""missing $styledDependency"""

        case GraphError.CircularDependency(node, dependency, _) =>
          val styledNode       = ansi.WhiteUnderlined(node.value.showTree)
          val styledDependency = ansi.White(dependency.value.showTree)
          s"""
${ansi.Magenta("Circular Dependency")} 
$styledNode
both requires ${ansi.Bold("and")} is transitively required by $styledDependency
    """
      }

  }

  object ExprGraph {
    def apply(layers: List[Node[LayerExpr]]): ExprGraph =
      ExprGraph(Graph(layers))

    def buildLayer[R: c.WeakTypeTag](layers: List[Node[LayerExpr]]): LayerExpr =
      ExprGraph(Graph(layers)).buildLayerFor(getRequirements[R])
  }

  implicit val exprLayerLike: LayerLike[LayerExpr] =
    new LayerLike[LayerExpr] {
      import c.universe._

      override def composeH(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        c.Expr(q"""$lhs ++ $rhs""")

      override def composeV(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
        c.Expr(q"""$lhs >>> $rhs""")
    }
}
