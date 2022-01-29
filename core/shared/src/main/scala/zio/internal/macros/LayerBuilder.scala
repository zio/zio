package zio.internal.macros

import zio.ZLayer.Debug
import zio._
import zio.internal.TerminalRendering
import zio.internal.TerminalRendering.LayerWiringError
import zio.internal.ansi.AnsiStringOps

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.annotation.tailrec

sealed trait ProvideMethod extends Product with Serializable {
  def isProvideSome: Boolean = this == ProvideMethod.ProvideSome
}

object ProvideMethod {
  case object Provide       extends ProvideMethod
  case object ProvideSome   extends ProvideMethod
  case object ProvideCustom extends ProvideMethod
}

final case class LayerBuilder[Type, Expr](
  target: List[Type],
  remainder: List[Type],
  providedLayers0: List[Expr],
  debugMap: PartialFunction[Expr, Debug],
  typeEquals: (Type, Type) => Boolean,
  foldTree: LayerTree[Expr] => Expr,
  method: ProvideMethod,
  exprToNode: Expr => Node[Type, Expr],
  typeToNode: Type => Node[Type, Expr],
  showExpr: Expr => String,
  showType: Type => String,
  reportWarn: String => Unit,
  reportError: String => Nothing
) {

  lazy val remainderNodes: List[Node[Type, Expr]] =
    remainder.map(typeToNode).distinct

  val (providedLayers, maybeDebug): (List[Expr], Option[ZLayer.Debug]) = {
    val maybeDebug = providedLayers0.collectFirst(debugMap)
    val layers     = providedLayers0.filterNot(debugMap.isDefinedAt)
    (layers.distinct, maybeDebug)
  }

  val providedLayerNodes: List[Node[Type, Expr]] = providedLayers.map(exprToNode)

  def build: Expr = {
    assertNoAmbiguity()

    /**
     * Build the layer tree. This represents the structure of a successfully
     * constructed ZLayer that will build the target types. This, of course, may
     * fail with one or more GraphErrors.
     */
    val layerTreeEither: Either[::[GraphError[Type, Expr]], LayerTree[Expr]] = {
      val nodes: List[Node[Type, Expr]] = providedLayerNodes ++ remainderNodes
      val graph                         = Graph(nodes, typeEquals)
      val result                        = graph.buildComplete(target)
      result
    }

    layerTreeEither match {
      case Left(buildErrors) =>
        reportBuildErrors(buildErrors)

      case Right(tree) =>
        warnUnused(tree)
        maybeDebug.foreach(debugLayer(_, tree))
        foldTree(tree)
    }
  }

  /**
   * Checks to see if any type is provided by multiple layers. If so, this will
   * report a compilation error about ambiguous layers. Otherwise, our algorithm
   * would arbitrarily choose one of the layers to satisfy the type, possibly
   * confusing the user and breaking stuff.
   */
  def assertNoAmbiguity(): Unit = {
    val typesToExprs: Map[String, List[String]] =
      groupMap(providedLayerNodes.flatMap { node =>
        node.outputs.map(output => showType(output) -> showExpr(node.value))
      })(_._1)(_._2)

    val duplicates: List[(String, List[String])] =
      typesToExprs.toList.filter { case (_, list) => list.size > 1 }

    if (duplicates.nonEmpty) {
      val message = "\n" + TerminalRendering.ambiguousLayersError(duplicates)
      reportError(message)
    }
  }

  /**
   * Given a LayerTree, this will warn about all provided layers that are not
   * used. This will also warn about any specified remainders that aren't
   * actually required, in the case of provideSome/provideCustom.
   */
  private def warnUnused(tree: LayerTree[Expr]): Unit = {
    val usedLayers       = tree.toSet
    val unusedUserLayers = providedLayers.toSet -- usedLayers -- remainderNodes.map(_.value)

    if (unusedUserLayers.nonEmpty) {
      val message = "\n" + TerminalRendering.unusedLayersError(unusedUserLayers.map(showExpr).toList)
      reportWarn(message)
    }

    val unusedRemainderLayers = remainderNodes.filterNot(node => usedLayers(node.value))

    method match {
      case ProvideMethod.Provide => ()
      case ProvideMethod.ProvideSome =>
        if (unusedRemainderLayers.nonEmpty) {
          val message = "\n" + TerminalRendering.unusedProvideSomeLayersError(
            unusedRemainderLayers.map(node => showType(node.outputs.head))
          )
          reportWarn(message)
        }

        if (remainder.isEmpty) {
          val message = "\n" + TerminalRendering.provideSomeNothingEnvError
          reportWarn(message)
        }
      case ProvideMethod.ProvideCustom =>
        if (unusedRemainderLayers.length == remainderNodes.length) {
          val message = "\n" + TerminalRendering.superfluousProvideCustomError
          reportWarn(message)
        }
    }
  }

  private def reportBuildErrors(errors: ::[GraphError[Type, Expr]]): Nothing = {
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
      reportError(TerminalRendering.circularityError(circularErrors))
    else
      reportError(TerminalRendering.missingLayersError(topLevelErrors, transitive, method.isProvideSome))
  }

  /**
   * Return only the first level of circular dependencies, as these will be the
   * most relevant.
   */
  private def sortErrors(errors: ::[GraphError[Type, Expr]]): Chunk[GraphError[Type, Expr]] = {
    val (circularDependencyErrors, otherErrors) =
      NonEmptyChunk.fromIterable(errors.head, errors.tail).distinct.partitionMap {
        case circularDependency: GraphError.CircularDependency[Type, Expr] => Left(circularDependency)
        case other                                                         => Right(other)
      }
    val sorted                = circularDependencyErrors.sortBy(_.depth)
    val initialCircularErrors = sorted.takeWhile(_.depth == sorted.headOption.map(_.depth).getOrElse(0))

    val (transitiveDepErrors, remainingErrors) = otherErrors.partitionMap {
      case es: GraphError.MissingTransitiveDependencies[Type, Expr] => Left(es)
      case other                                                    => Right(other)
    }

    val groupedTransitiveErrors = transitiveDepErrors.groupBy(_.node).map { case (node, errors) =>
      val layer = errors.flatMap(_.dependency)
      GraphError.MissingTransitiveDependencies(node, layer)
    }

    initialCircularErrors ++ groupedTransitiveErrors ++ remainingErrors
  }

  private def renderError(error: GraphError[Type, Expr]): LayerWiringError =
    error match {
      case GraphError.MissingTransitiveDependencies(node, dependencies) =>
        LayerWiringError.MissingTransitive(showExpr(node.value), dependencies.map(showType).toList)

      case GraphError.MissingTopLevelDependency(dependency) =>
        LayerWiringError.MissingTopLevel(showType(dependency))

      case GraphError.CircularDependency(node, dependency, _) =>
        LayerWiringError.Circular(showExpr(node.value), showExpr(dependency.value))
    }

  /**
   * Emits a compilation notice with a visual representation of the constructed
   * Layer.
   */
  private def debugLayer(debug: ZLayer.Debug, tree: LayerTree[Expr]): Unit = {
    val renderedTree: String =
      tree
        .map(expr => RenderedGraph(showExpr(expr)))
        .fold[RenderedGraph](RenderedGraph.Row(List.empty), identity, _ ++ _, _ >>> _)
        .render

    val title   = "  ZLayer Wiring Graph  ".yellow.bold.inverted
    val builder = new StringBuilder
    builder ++= "\n" + title + "\n\n" + renderedTree + "\n\n"

    if (debug == ZLayer.Debug.Mermaid) {
      val mermaidLink: String = generateMermaidJsLink(tree)
      builder ++= "Mermaid Live Editor Link".underlined + "\n" + mermaidLink.faint + "\n\n"
    }

    reportWarn(builder.result())
  }

  /**
   * Generates a link of the layer graph for the Mermaid.js graph viz library's
   * live-editor (https://mermaid-js.github.io/mermaid-live-editor)
   */
  private def generateMermaidJsLink(
    tree: LayerTree[Expr]
  ): String = {

    val map = tree
      .map(showExpr(_))
      .fold[Map[String, Chunk[String]]](
        z = Map.empty,
        value = str => Map(str -> Chunk.empty),
        composeH = _ ++ _,
        composeV = (m1, m2) =>
          m2.map { case (key, values) =>
            val result = m1.keys.toSet -- m1.values.flatten.toSet
            key -> (values ++ Chunk.fromIterable(result))
          } ++ m1
      )

    val mermaidCode: String =
      map.flatMap {
        case (key, children) if children.isEmpty =>
          List(s"    $key")
        case (key, children) =>
          children.map { child =>
            s"    $key --> $child"
          }
      }
        .mkString("\\n")

    val mermaidGraph =
      s"""{"code":"graph\\n$mermaidCode\\n    ","mermaid": "{\\n  \\"theme\\": \\"default\\"\\n}", "updateEditor": true, "autoSync": true, "updateDiagram": true}"""

    val encodedMermaidGraph: String =
      new String(Base64.getEncoder.encode(mermaidGraph.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)

    val mermaidLink = s"https://mermaid-js.github.io/mermaid-live-editor/edit/#$encodedMermaidGraph"
    mermaidLink
  }

  def groupMap[A, K, B](as: List[A])(key: A => K)(f: A => B): Map[K, List[B]] = {
    @tailrec
    def loop(as: List[A], acc: Map[K, List[B]]): Map[K, List[B]] =
      as match {
        case a :: as =>
          val k = key(a)
          loop(as, acc.updated(k, f(a) :: acc.getOrElse(k, List.empty)))
        case Nil =>
          acc
      }

    loop(as, Map.empty[K, List[B]])
  }

}
