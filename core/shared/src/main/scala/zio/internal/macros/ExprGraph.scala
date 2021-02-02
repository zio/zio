package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

trait ExprLike[A] {
  def showTree(expr: A): String
  def compileError(message: String): Nothing
}

final case class ExprGraph[A](graph: Graph[A])(implicit exprLike: ExprLike[A], layerLike: LayerLike[A]) {
  def buildLayerFor(output: List[String]): A =
    if (output.isEmpty)
      layerLike.empty
    else
      graph.buildComplete(output) match {
        case Left(errors) => exprLike.compileError(renderErrors(errors))
        case Right(value) => value
      }

  private def renderErrors(errors: ::[GraphError[A]]): String = {
    val allErrors = sortErrors(errors)

    val errorMessage =
      allErrors
        .map(renderError)
        .mkString("\n")
        .linesIterator
        .mkString("\n")
    s"""
${"ZLayer Auto Assemble".yellow.underlined}
$errorMessage

"""
  }

  /**
   * Return only the first level of circular dependencies, as these will be the most relevant.
   */
  private def sortErrors(errors: ::[GraphError[A]]): Chunk[GraphError[A]] = {
    val (circularDependencyErrors, otherErrors) =
      NonEmptyChunk.fromIterable(errors.head, errors.tail).distinct.partitionMap {
        case circularDependency: GraphError.CircularDependency[A] =>
          Left(circularDependency)
        case other => Right(other)
      }
    val sorted                = circularDependencyErrors.sortBy(_.depth)
    val initialCircularErrors = sorted.takeWhile(_.depth == sorted.headOption.map(_.depth).getOrElse(0))

    initialCircularErrors ++ otherErrors
  }

  private def renderError(error: GraphError[A]): String =
    error match {
      case GraphError.MissingDependency(node, dependency) =>
        val styledDependency = dependency.white.bold
        val styledLayer      = exprLike.showTree(node.value).white
        s"""
${"missing".faint} $styledDependency
    ${"for".faint} $styledLayer"""

      case GraphError.MissingTopLevelDependency(dependency) =>
        val styledDependency = dependency.white.bold
        s"""
${"missing".faint} $styledDependency"""

      case GraphError.CircularDependency(node, dependency, _) =>
        val styledNode       = exprLike.showTree(node.value).white.bold
        val styledDependency = exprLike.showTree(dependency.value).white
        s"""
${"Circular Dependency".yellow} 
$styledNode both requires ${"and".bold} is transitively required by $styledDependency"""
    }
}

object ExprGraph {
  def apply[A: LayerLike: ExprLike](layers: List[Node[A]]): ExprGraph[A] =
    new ExprGraph(Graph(layers))
}
