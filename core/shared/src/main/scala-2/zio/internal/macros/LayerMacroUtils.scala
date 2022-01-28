package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.reflect.macros.blackbox

private[zio] trait LayerMacroUtils {
  val c: blackbox.Context
  import c.universe._

  type LayerExpr = c.Expr[ZLayer[_, _, _]]

  def generateExprGraph(
    layer: Seq[LayerExpr],
    provideMethod: ProvideMethod[LayerExpr]
  ): ZLayerExprBuilder[c.Type, LayerExpr] =
    generateExprGraph(layer.map(getNode).toList, provideMethod)

  def generateExprGraph(
    nodes: List[Node[c.Type, LayerExpr]],
    provideMethod: ProvideMethod[LayerExpr]
  ): ZLayerExprBuilder[c.Type, LayerExpr] =
    ZLayerExprBuilder[c.Type, LayerExpr](
      graph = Graph(
        nodes = nodes,
        // They must be `.toString`-ed as a backup in the case of refinement
        // types. Otherwise, [[examples.DumbExample]] will fail.
        keyEquals = (t1, t2) => t1 <:< t2 || (t1.toString == t2.toString)
      ),
      showKey = tpe => tpe.toString,
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      abort = c.abort(c.enclosingPosition, _),
      warn = c.warning(c.enclosingPosition, _),
      emptyExpr = reify(ZLayer.succeed(())),
      composeH = (lhs, rhs) => c.Expr(q"""$lhs ++ $rhs"""),
      composeV = (lhs, rhs) => c.Expr(q"""$lhs >>> $rhs"""),
      provideMethod = provideMethod
    )

  def buildMemoizedLayer(
    exprGraph: ZLayerExprBuilder[c.Type, LayerExpr],
    requirements: List[c.Type]
  ): LayerExpr = {
    // This is run for its side effects: Reporting compile errors with the original source names.
    val _: LayerExpr = exprGraph.buildLayerFor(requirements, true)

    val nodes = exprGraph.graph.nodes
    val memoizedNodes = nodes.map { node =>
      val freshName = c.freshName("layer")
      val termName  = TermName(freshName)
      node.copy(value = c.Expr[ZLayer[_, _, _]](q"$termName"))
    }

    val definitions = memoizedNodes.zip(nodes).map { case (memoizedNode, node) =>
      // val layer0 = intLayer
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

  def getNode(layer: LayerExpr): Node[c.Type, LayerExpr] = {
    val typeArgs = layer.actualType.dealias.typeArgs
    // ZIO[in, _, out]
    val in  = typeArgs.head
    val out = typeArgs(2)
    Node(getRequirements(in), getRequirements(out), layer)
  }

  def getRequirements[T: c.WeakTypeTag]: List[c.Type] =
    getRequirements(weakTypeOf[T])

  def provideBaseImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layers: Seq[c.Expr[ZLayer[_, E, _]]],
    method: String,
    isProvideSome: Boolean = false
  ): c.Expr[F[R0, E, A]] = {

    val targetTypes    = getRequirements[R]
    val remainderTypes = getRequirements[R0]

    val debugMap: PartialFunction[LayerExpr, ZLayer.Debug] =
      ((_: LayerExpr).tree match {
        case q"zio.ZLayer.Debug.tree"    => Some(ZLayer.Debug.Tree)
        case q"zio.ZLayer.Debug.mermaid" => Some(ZLayer.Debug.Mermaid)
        case _                           => None
      }).unlift

    val builder = LayerBuilder[c.Type, LayerExpr](
      target = targetTypes,
      remainder = remainderTypes,
      providedLayers0 = layers.toList,
      debugMap = debugMap,
      typeEquals = _ <:< _,
      foldTree = buildFinalTree,
      method = if (isProvideSome) Method.ProvideSome else Method.ProvideCustom,
      exprToNode = getNode,
      typeToNode = tpe => Node(Nil, List(tpe), c.Expr[ZLayer[_, E, _]](q"ZLayer.environment[$tpe]")),
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      showType = _.toString,
      reportWarn = c.warning(c.enclosingPosition, _),
      reportError = c.abort(c.enclosingPosition, _)
    )

    val expr = builder.build
    c.Expr[F[R0, E, A]](q"${c.prefix}.${TermName(method)}(${expr.tree})")
  }

  private def buildFinalTree(tree: LayerTree[LayerExpr]) = {
    val memoMap =
      tree.toList.map { node =>
        val freshName = c.freshName("layer")
        val termName  = TermName(freshName)
        node -> c.Expr[ZLayer[_, _, _]](q"$termName")
      }.toMap

    val definitions = memoMap.map { case (expr, memoizedNode) =>
      ValDef(
        Modifiers(),
        TermName(memoizedNode.tree.toString()),
        TypeTree(),
        expr.tree
      )
    }

    val layerExpr = tree.fold[LayerExpr](
      z = reify(ZLayer.succeed(())),
      value = memoMap(_),
      composeH = (lhs, rhs) => c.Expr(q"""$lhs ++ $rhs"""),
      composeV = (lhs, rhs) => c.Expr(q"""$lhs >>> $rhs""")
    )

    c.Expr(q"""
    ..$definitions
    ${layerExpr.tree}
    """)
  }

  def constructLayer[R0: c.WeakTypeTag, R: c.WeakTypeTag, E](
    layers0: Seq[c.Expr[ZLayer[_, E, _]]],
    isProvideSome: Boolean = false
  ): c.Expr[ZLayer[Any, E, R]] = {
    assertProperVarArgs(layers0)

    val maybeDebug = layers0.collectFirst {
      _.tree match {
        case q"zio.ZLayer.Debug.tree"    => ZLayer.Debug.Tree
        case q"zio.ZLayer.Debug.mermaid" => ZLayer.Debug.Mermaid
      }
    }
    val layers = layers0.filter {
      _.tree match {
        case q"zio.ZLayer.Debug.tree" | q"zio.ZLayer.Debug.mermaid" => false
        case _                                                      => true
      }
    }

    val remainderNodes =
      getRequirements[R0].map { tpe =>
        Node(
          List.empty,
          List(tpe),
          c.Expr[ZLayer[_, E, _]](q"_root_.zio.ZLayer.environment[$tpe]")
        )
      }

    val provideMethod: ProvideMethod[c.Expr[ZLayer[_, E, _]]] =
      if (isProvideSome)
        ProvideMethod.ProvideSome(remainderNodes.map(_.value))
      else ProvideMethod.Provide

//      if (weakTypeOf[R0] =:= weakTypeOf[Any]) List.empty
//      else List(Node(List.empty, getRequirements[R0], remainderExpr))

    val nodes = remainderNodes ++ layers.map(getNode)

    val graph        = generateExprGraph(nodes, provideMethod)
    val requirements = getRequirements[R]
    val expr         = buildMemoizedLayer(graph, requirements)
    maybeDebug.foreach { debug =>
      debugLayer(debug, graph, requirements)
    }
    expr.asInstanceOf[c.Expr[ZLayer[Any, E, R]]]
  }

  private def debugLayer(
    debug: ZLayer.Debug,
    graph: ZLayerExprBuilder[c.Type, LayerExpr],
    requirements: List[c.Type]
  ): Unit = {
    val graphString: String =
      eitherToOption(
        graph.graph
          .map(layer => RenderedGraph(layer.showTree))
          .buildComplete(requirements)
      ).get
        .fold[RenderedGraph](RenderedGraph.Row(List.empty), identity, _ ++ _, _ >>> _)
        .render

    val title   = "  ZLayer Wiring Graph  ".yellow.bold.inverted
    val builder = new StringBuilder
    builder ++= "\n" + title + "\n\n" + graphString + "\n\n"

    if (debug == ZLayer.Debug.Mermaid) {
      val mermaidLink: String = generateMermaidJsLink(requirements, graph)
      builder ++= "Mermaid Live Editor Link".underlined + "\n" + mermaidLink.faint + "\n\n"
    }

    c.info(c.enclosingPosition, builder.result(), true)
  }

  /**
   * Scala 2.11 doesn't have `Either.toOption`
   */
  private def eitherToOption[A](either: Either[_, A]): Option[A] = either match {
    case Left(_)      => None
    case Right(value) => Some(value)
  }

  def getRequirements(tpe: Type): List[c.Type] = {
    val intersectionTypes = tpe.dealias.map(_.dealias).intersectionTypes

    intersectionTypes
      .map(_.dealias)
      .filterNot(_.isAny)
      .distinct
  }

  def assertProperVarArgs(layer: Seq[c.Expr[_]]): Unit = {
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

  implicit class TreeOps(self: c.Expr[_]) {
    def showTree: String = CleanCodePrinter.show(c)(self.tree)
  }

  /**
   * Generates a link of the layer graph for the Mermaid.js graph viz library's
   * live-editor (https://mermaid-js.github.io/mermaid-live-editor)
   */
  private def generateMermaidJsLink[R: c.WeakTypeTag, R0: c.WeakTypeTag, E](
    requirements: List[c.Type],
    graph: ZLayerExprBuilder[c.Type, LayerExpr]
  ): String = {
    val cool = eitherToOption(
      graph.graph
        .map(layer => layer.showTree)
        .buildComplete(requirements)
    ).get

    val map = cool.fold[Map[String, Chunk[String]]](
      z = Map.empty,
      value = str => Map(str -> Chunk.empty),
      composeH = _ ++ _,
      composeV = (m1, m2) =>
        m2.map { case (key, values) =>
          val result = m1.keys.toSet -- m1.values.flatten.toSet
          key -> (values ++ Chunk.fromIterable(result))
        } ++ m1
    )

    val mermaidGraphString = map.flatMap {
      case (key, children) if children.isEmpty =>
        List(s"    $key")
      case (key, children) =>
        children.map { child =>
          s"    $key --> $child"
        }
    }
      .mkString("\\n")

    val mermaidGraph =
      s"""{"code":"graph\\n$mermaidGraphString\\n    ","mermaid": "{\\n  \\"theme\\": \\"default\\"\\n}", "updateEditor": true, "autoSync": true, "updateDiagram": true}"""

    val encodedMermaidGraph: String =
      new String(Base64.getEncoder.encode(mermaidGraph.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)

    val mermaidLink = s"https://mermaid-js.github.io/mermaid-live-editor/edit/#$encodedMermaidGraph"
    mermaidLink
  }

}

trait ExprGraphCompileVariants {}
