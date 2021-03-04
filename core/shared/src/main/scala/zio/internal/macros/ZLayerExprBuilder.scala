package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

final case class ZLayerExprBuilder[A](
  graph: Graph[A],
  showExpr: A => String,
  abort: String => Nothing,
  emptyExpr: A,
  composeH: (A, A) => A,
  composeV: (A, A) => A
) {
  def buildLayerFor(output: List[String]): A =
    if (output.isEmpty)
      emptyExpr
    else
      graph.buildComplete(output) match {
        case Left(errors) => abort(renderErrors(errors))
        case Right(value) => value.fold(emptyExpr, identity, composeH, composeV)
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
        case circularDependency: GraphError.CircularDependency[A] => Left(circularDependency)
        case other                                                => Right(other)
      }
    val sorted                = circularDependencyErrors.sortBy(_.depth)
    val initialCircularErrors = sorted.takeWhile(_.depth == sorted.headOption.map(_.depth).getOrElse(0))

    initialCircularErrors ++ otherErrors.sortBy(_.isInstanceOf[GraphError.MissingDependency[A]])
  }

  private def renderError(error: GraphError[A]): String =
    error match {
      case GraphError.MissingDependency(node, dependency) =>
        val styledDependency = dependency.white.bold
        val styledLayer      = showExpr(node.value).white
        s"""
${"missing".faint} $styledDependency
    ${"for".faint} $styledLayer"""

      case GraphError.MissingTopLevelDependency(dependency) =>
        val styledDependency = dependency.white.bold
        s"""
${"missing".faint} $styledDependency"""

      case GraphError.CircularDependency(node, dependency, _) =>
        val styledNode       = showExpr(node.value).white.bold
        val styledDependency = showExpr(dependency.value).white
        s"""
${"Circular Dependency".yellow} 
$styledNode both requires ${"and".bold} is transitively required by $styledDependency"""
    }
}

object ZLayerExprBuilder extends ExprGraphCompileVariants {}
