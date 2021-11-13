package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.reflect.macros.blackbox

private[zio] trait ServiceBuilderMacroUtils {
  val c: blackbox.Context
  import c.universe._

  type ServiceBuilderExpr = c.Expr[ZServiceBuilder[_, _, _]]

  def generateExprGraph(
    serviceBuilder: Seq[ServiceBuilderExpr]
  ): ZServiceBuilderExprBuilder[c.Type, ServiceBuilderExpr] =
    generateExprGraph(serviceBuilder.map(getNode).toList)

  def generateExprGraph(
    nodes: List[Node[c.Type, ServiceBuilderExpr]]
  ): ZServiceBuilderExprBuilder[c.Type, ServiceBuilderExpr] =
    ZServiceBuilderExprBuilder[c.Type, ServiceBuilderExpr](
      graph = Graph(
        nodes = nodes,
        // They must be `.toString`-ed as a backup in the case of refinement
        // types. Otherwise, [[examples.DumbExample]] will fail.
        keyEquals = (t1, t2) => t1 =:= t2 || (t1.toString == t2.toString)
      ),
      showKey = tpe => tpe.toString,
      showExpr = expr => CleanCodePrinter.show(c)(expr.tree),
      abort = c.abort(c.enclosingPosition, _),
      emptyExpr = reify(ZServiceBuilder.succeed(())),
      composeH = (lhs, rhs) => c.Expr(q"""$lhs ++ $rhs"""),
      composeV = (lhs, rhs) => c.Expr(q"""$lhs >>> $rhs""")
    )

  def buildMemoizedServiceBuilder(
    exprGraph: ZServiceBuilderExprBuilder[c.Type, ServiceBuilderExpr],
    requirements: List[c.Type]
  ): ServiceBuilderExpr = {
    // This is run for its side effects: Reporting compile errors with the original source names.
    val _ = exprGraph.buildServiceBuilderFor(requirements)

    val nodes = exprGraph.graph.nodes
    val memoizedNodes = nodes.map { node =>
      val freshName = c.freshName("serviceBuilder")
      val termName  = TermName(freshName)
      node.copy(value = c.Expr[ZServiceBuilder[_, _, _]](q"$termName"))
    }

    val definitions = memoizedNodes.zip(nodes).map { case (memoizedNode, node) =>
      ValDef(Modifiers(), TermName(memoizedNode.value.tree.toString()), TypeTree(), node.value.tree)
    }
    val serviceBuilderExpr = exprGraph
      .copy(graph = Graph[c.Type, ServiceBuilderExpr](memoizedNodes, exprGraph.graph.keyEquals))
      .buildServiceBuilderFor(requirements)

    c.Expr(q"""
    ..$definitions
    ${serviceBuilderExpr.tree}
    """)
  }

  def getNode(serviceBuilder: ServiceBuilderExpr): Node[c.Type, ServiceBuilderExpr] = {
    val typeArgs = serviceBuilder.actualType.dealias.typeArgs
    // ZIO[in, _, out]
    val in  = typeArgs.head
    val out = typeArgs(2)
    Node(getRequirements(in), getRequirements(out), serviceBuilder)
  }

  def getRequirements[T: c.WeakTypeTag]: List[c.Type] =
    getRequirements(weakTypeOf[T])

  def isValidHasType(tpe: Type): Boolean =
    tpe.isHas || tpe.isAny

  def injectBaseImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    serviceBuilder: Seq[c.Expr[ZServiceBuilder[_, E, _]]],
    method: String
  ): c.Expr[F[R0, E, A]] = {
    val expr = constructServiceBuilder[R0, R, E](serviceBuilder)
    c.Expr[F[R0, E, A]](q"${c.prefix}.${TermName(method)}(${expr.tree})")
  }

  def constructServiceBuilder[R0: c.WeakTypeTag, R: c.WeakTypeTag, E](
    serviceBuilder0: Seq[c.Expr[ZServiceBuilder[_, E, _]]]
  ): c.Expr[ZServiceBuilder[Any, E, R]] = {
    assertProperVarArgs(serviceBuilder0)

    val debug = serviceBuilder0.collectFirst {
      _.tree match {
        case q"zio.ZServiceBuilder.Debug.tree"    => ZServiceBuilder.Debug.Tree
        case q"zio.ZServiceBuilder.Debug.mermaid" => ZServiceBuilder.Debug.Mermaid
      }
    }
    val serviceBuilder = serviceBuilder0.filter {
      _.tree match {
        case q"zio.ZServiceBuilder.Debug.tree" | q"zio.ZServiceBuilder.Debug.mermaid" => false
        case _                                                                        => true
      }
    }

    val remainderExpr =
      if (weakTypeOf[R0] =:= weakTypeOf[ZEnv]) reify(ZEnv.any)
      else reify(ZServiceBuilder.environment[R0])
    val remainderNode =
      if (weakTypeOf[R0] =:= weakTypeOf[Any]) List.empty
      else List(Node(List.empty, getRequirements[R0], remainderExpr))
    val nodes = remainderNode ++ serviceBuilder.map(getNode)

    val graph        = generateExprGraph(nodes)
    val requirements = getRequirements[R]
    val expr         = buildMemoizedServiceBuilder(graph, requirements)
    debug.foreach { debug =>
      debugServiceBuilder(debug, graph, requirements)
    }
    expr.asInstanceOf[c.Expr[ZServiceBuilder[Any, E, R]]]
  }

  private def debugServiceBuilder(
    debug: ZServiceBuilder.Debug,
    graph: ZServiceBuilderExprBuilder[c.Type, ServiceBuilderExpr],
    requirements: List[c.Type]
  ): Unit = {
    val graphString: String =
      eitherToOption(
        graph.graph
          .map(serviceBuilder => RenderedGraph(serviceBuilder.showTree))
          .buildComplete(requirements)
      ).get
        .fold[RenderedGraph](RenderedGraph.Row(List.empty), identity, _ ++ _, _ >>> _)
        .render

    val title   = "  ZServiceBuilder Wiring Graph  ".yellow.bold.inverted
    val builder = new StringBuilder
    builder ++= "\n" + title + "\n\n" + graphString + "\n\n"

    if (debug == ZServiceBuilder.Debug.Mermaid) {
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

    intersectionTypes.filter(!isValidHasType(_)) match {
      case Nil => ()
      case nonHasTypes =>
        c.abort(
          c.enclosingPosition,
          s"\nContains non-Has types:\n- ${nonHasTypes.map(_.toString.yellow).mkString("\n- ")}"
        )
    }

    intersectionTypes
      .filter(_.isHas)
      .map(_.dealias.typeArgs.head.dealias)
      .distinct
  }

  def assertProperVarArgs(serviceBuilder: Seq[c.Expr[_]]): Unit = {
    val _ = serviceBuilder.map(_.tree) collect { case Typed(_, Ident(typeNames.WILDCARD_STAR)) =>
      c.abort(
        c.enclosingPosition,
        "Auto-construction cannot work with `someList: _*` syntax.\nPlease pass the service builders themselves into this method."
      )
    }
  }

  implicit class TypeOps(self: Type) {
    def isHas: Boolean = self.dealias.typeSymbol == typeOf[Has[_]].typeSymbol

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
   * Generates a link of the service builder graph for the Mermaid.js graph viz
   * library's live-editor (https://mermaid-js.github.io/mermaid-live-editor)
   */
  private def generateMermaidJsLink[R <: Has[_]: c.WeakTypeTag, R0: c.WeakTypeTag, E](
    requirements: List[c.Type],
    graph: ZServiceBuilderExprBuilder[c.Type, ServiceBuilderExpr]
  ): String = {
    val cool = eitherToOption(
      graph.graph
        .map(serviceBuilder => serviceBuilder.showTree)
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
