package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

final case class ZLayerExprBuilder[Key, A](
  graph: Graph[Key, A],
  showKey: Key => String,
  showExpr: A => String,
  abort: String => Nothing,
  emptyExpr: A,
  composeH: (A, A) => A,
  composeV: (A, A) => A
) {
  def buildLayerFor(output: List[Key]): A =
    output match {
      case Nil => emptyExpr
      case output =>
        assertNoDuplicateOutputs()

        graph.buildComplete(output) match {
          case Left(errors) => reportGraphErrors(errors)

          case Right(composed) =>
            assertNoLeftovers(composed)
            composed.fold(emptyExpr, identity, composeH, composeV)
        }
    }

  private def assertNoLeftovers(layerCompose: LayerCompose[A]): Unit = {
    val used      = layerCompose.toSet
    val leftovers = graph.nodes.filterNot(node => used.contains(node.value))

    if (leftovers.nonEmpty) {
      val message = leftovers.map { node =>
        s"${"unused".underlined} ${showExpr(node.value).blue.bold}"
      }
        .mkString("\n")

      reportErrorMessage(message)
    }
  }

  private def assertNoDuplicateOutputs(): Unit = {
    val outputMap: Map[Key, List[Node[Key, A]]] = (for {
      node   <- graph.nodes
      output <- node.outputs
    } yield output -> node)
      .groupBy(_._1)
      .map { case (key, value) => key -> value.map(_._2) }
      .filter(_._2.length >= 2)

    if (outputMap.nonEmpty) {
      val message = outputMap.map { case (output, nodes) =>
        s"${output.toString.cyan} is provided by multiple layers:\n" +
          nodes.map(node => "— " + showExpr(node.value).bold.cyan).mkString("\n")
      }
        .mkString("\n")

      reportErrorMessage(message)
    }
  }

  private def reportErrorMessage(errorMessage: String): Nothing = {
    val body = errorMessage
      .split("\n")
      .map { line =>
        if (line.forall(_.isWhitespace))
          line
        else
          "❯ ".red + line
      }
      .mkString("\n")

    abort(s"""

${s"  ZLayer Wiring Error  ".red.inverted.bold}

$body

""")
  }

  private def reportGraphErrors(errors: ::[GraphError[Key, A]]): Nothing = {
    val allErrors = sortErrors(errors)

    val errorMessage =
      allErrors
        .map(renderError)
        .mkString("\n\n")

    reportErrorMessage(errorMessage)
  }

  /**
   * Return only the first level of circular dependencies, as these will be the most relevant.
   */
  private def sortErrors(errors: ::[GraphError[Key, A]]): Chunk[GraphError[Key, A]] = {
    val (circularDependencyErrors, otherErrors) =
      NonEmptyChunk.fromIterable(errors.head, errors.tail).distinct.partitionMap {
        case circularDependency: GraphError.CircularDependency[Key, A] => Left(circularDependency)
        case other                                                     => Right(other)
      }
    val sorted                = circularDependencyErrors.sortBy(_.depth)
    val initialCircularErrors = sorted.takeWhile(_.depth == sorted.headOption.map(_.depth).getOrElse(0))

    initialCircularErrors ++ otherErrors.sortBy(_.isInstanceOf[GraphError.MissingTransitiveDependency[Key, A]])
  }

  private def renderError(error: GraphError[Key, A]): String =
    error match {
      case GraphError.MissingTransitiveDependency(node, dependency) =>
        val styledDependency = showKey(dependency).blue.bold
        val styledLayer      = showExpr(node.value).blue
        s"""${"missing".underlined} $styledDependency
    ${"for".underlined} $styledLayer"""

      case GraphError.MissingTopLevelDependency(dependency) =>
        val styledDependency = showKey(dependency).blue.bold
        s"""${"missing".underlined} $styledDependency"""

      case GraphError.CircularDependency(node, dependency, _) =>
        val styledNode       = showExpr(node.value).blue.bold
        val styledDependency = showExpr(dependency.value).blue
        s"""
${"Circular Dependency".blue}
$styledNode both requires ${"and".bold} is transitively required by $styledDependency"""
    }
}

object ZLayerExprBuilder extends ExprGraphCompileVariants {}
