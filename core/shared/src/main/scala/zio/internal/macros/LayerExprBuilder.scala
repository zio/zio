package zio.internal.macros

import zio._
import zio.internal.TerminalRendering
import zio.internal.TerminalRendering.LayerWiringError
import zio.internal.ansi.AnsiStringOps

final case class ZLayerExprBuilder[Key, A](
  graph: Graph[Key, A],
  showKey: Key => String,
  showExpr: A => String,
  abort: String => Nothing,
  warn: String => Unit,
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
      val message = "\n" + TerminalRendering.unusedLayersError(leftovers.map(node => showExpr(node.value)))

      warn(message)
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
        if (line.forall(_.isWhitespace)) line
        else "❯ ".red + line
      }
      .mkString("\n")

    abort(s"""

${s"  ZLayer Wiring Error  ".red.inverted.bold}

$body

""")
  }

  private def reportWarning(warning: String): Unit = {
    val body = warning
      .split("\n")
      .map { line =>
        if (line.forall(_.isWhitespace)) line
        else "❯ ".yellow + line
      }
      .mkString("\n")

    warn(s"""

${s"  ZLayer Wiring Warning  ".yellow.inverted.bold}

$body

""")
  }

  private def reportGraphErrors(errors: ::[GraphError[Key, A]]): Nothing = {
    val allErrors = sortErrors(errors).map(renderError).toList

    val topLevelErrors = allErrors.collect { case top: LayerWiringError.MissingTopLevel =>
      top.layer
    }

    val transitive = allErrors.collect { case LayerWiringError.MissingTransitive(layer, deps) =>
      layer -> deps
    }.groupBy(_._1).map { case (key, value) => key -> value.flatMap(_._2) }

    val circularErrors = allErrors.collect { case LayerWiringError.Circular(layer, dep) =>
      layer -> dep
    }

    if (circularErrors.nonEmpty)
      abort(TerminalRendering.circularityError(circularErrors))
    else
      abort(TerminalRendering.missingLayersError(topLevelErrors, transitive))
  }

  /**
   * Return only the first level of circular dependencies, as these will be the
   * most relevant.
   */
  private def sortErrors(errors: ::[GraphError[Key, A]]): Chunk[GraphError[Key, A]] = {
    val (circularDependencyErrors, otherErrors) =
      NonEmptyChunk.fromIterable(errors.head, errors.tail).distinct.partitionMap {
        case circularDependency: GraphError.CircularDependency[Key, A] => Left(circularDependency)
        case other                                                     => Right(other)
      }
    val sorted                = circularDependencyErrors.sortBy(_.depth)
    val initialCircularErrors = sorted.takeWhile(_.depth == sorted.headOption.map(_.depth).getOrElse(0))

    val (transitiveDepErrors, remainingErrors) = otherErrors.partitionMap {
      case es: GraphError.MissingTransitiveDependencies[Key, A] => Left(es)
      case other                                                => Right(other)
    }

    val groupedTransitiveErrors = transitiveDepErrors.groupBy(_.node).map { case (node, errors) =>
      val layer = errors.flatMap(_.dependency)
      GraphError.MissingTransitiveDependencies(node, layer)
    }

    initialCircularErrors ++ groupedTransitiveErrors ++ remainingErrors
  }

  private def renderError(error: GraphError[Key, A]): LayerWiringError =
    error match {
      case GraphError.MissingTransitiveDependencies(node, dependencies) =>
        LayerWiringError.MissingTransitive(showExpr(node.value), dependencies.map(showKey).toList)

      case GraphError.MissingTopLevelDependency(dependency) =>
        LayerWiringError.MissingTopLevel(showKey(dependency))

      case GraphError.CircularDependency(node, dependency, _) =>
        LayerWiringError.Circular(showExpr(node.value), showExpr(dependency.value))
      //        val styledNode       = showExpr(node.value).blue.bold
      //        val styledDependency = showExpr(dependency.value).blue
      //        s"""
      //${"Circular Dependency".blue}
      //$styledNode both requires ${"and".bold} is transitively required by $styledDependency"""
      //    }
    }
}

object ZLayerExprBuilder extends ExprGraphCompileVariants {}
