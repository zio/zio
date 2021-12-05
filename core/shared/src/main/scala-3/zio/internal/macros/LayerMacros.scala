package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import java.nio.charset.StandardCharsets
import java.util.Base64


import LayerMacroUtils._

object LayerMacros {
  def provideImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R,E,A]], layer: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[ZIO[R0,E,A]] = {
    val layerExpr = fromAutoImpl[R0, R, E](layer)
    '{$zio.provide($layerExpr.asInstanceOf[ZLayer[R0,E,R]])}
  }

  def fromAutoImpl[R0: Type, R: Type, E: Type](layer0: Expr[Seq[ZLayer[_,E,_]]])(using ctx: Quotes): Expr[ZLayer[R0,E,R]] = {
    val deferredRequirements = getRequirements[R0]("Specified Remainder")
    val requirements     = getRequirements[R](s"Target Environment")

    val (layer, debug) =
      layer0 match {
        case Varargs(layer0) =>
          val debug = layer0.collectFirst {
              case '{ZLayer.Debug.tree} => ZLayer.Debug.Tree
              case '{ZLayer.Debug.mermaid} => ZLayer.Debug.Mermaid
          }
          val layer = layer0.filter {
              case '{ZLayer.Debug.tree} | '{ZLayer.Debug.mermaid} => false
              case _ => true
          }
          (layer, debug)
      }

    val zEnvLayer: List[Node[ctx.reflect.TypeRepr, LayerExpr]] =
      if (deferredRequirements.nonEmpty) List(Node(List.empty, deferredRequirements, '{ZLayer.environment[R0]}))
      else List.empty

    val nodes = zEnvLayer ++ getNodes(layer)

    val graph = ZLayerExprBuilder.fromNodes(ctx)(nodes)
    val dep = buildMemoizedLayer(ctx)(graph, requirements)

    debug.foreach { debug =>
      debugLayer(debug, graph, requirements)
    }

    '{$dep.asInstanceOf[ZLayer[R0,E,R]] }
  }


  private def debugLayer(using ctx: Quotes)(
                          debug: ZLayer.Debug,
                          graph: ZLayerExprBuilder[ctx.reflect.TypeRepr, LayerExpr],
                          requirements: List[ctx.reflect.TypeRepr]
                        ): Unit = {
    import ctx.reflect._

    val graphString: String =

        graph.graph
          .map(layer => RenderedGraph(layer.show))
          .buildComplete(requirements)
        .toOption.get
        .fold[RenderedGraph](RenderedGraph.Row(List.empty), identity, _ ++ _, _ >>> _)
        .render

    val title   = "  ZLayer Wiring Graph  ".yellow.bold.inverted
    val builder = new StringBuilder
    builder ++= "\n" + title + "\n\n" + graphString + "\n\n"

    if (debug == ZLayer.Debug.Mermaid) {
      val mermaidLink: String = generateMermaidJsLink(requirements, graph)
      builder ++= "Mermaid Live Editor Link".underlined + "\n" + mermaidLink.faint + "\n\n"
    }

    report.info(builder.result())
  }

  /**
   * Generates a link of the layer graph for the Mermaid.js graph viz library's
   * live-editor (https://mermaid-js.github.io/mermaid-live-editor)
   */
  private def generateMermaidJsLink(using ctx: Quotes)(
    requirements: List[ctx.reflect.TypeRepr],
    graph: ZLayerExprBuilder[ctx.reflect.TypeRepr, LayerExpr]
  ): String = {
    val cool =
      graph.graph
        .map(layer => layer.show)
        .buildComplete(requirements)
    .toOption.get

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


trait ExprGraphCompileVariants { self : ZLayerExprBuilder.type =>
  def fromNodes(ctx: Quotes)(nodes: List[Node[ctx.reflect.TypeRepr, LayerExpr]]): ZLayerExprBuilder[ctx.reflect.TypeRepr, LayerExpr] = {
    import ctx.reflect._
    implicit val qcx: ctx.type = ctx

    def renderTypeRepr(typeRepr: TypeRepr)(using Quotes): String = {
      import quotes.reflect._
      typeRepr.show
    }

    def compileError(message: String) : Nothing = report.errorAndAbort(message)
    def compileWarning(message: String) : Unit = report.warning(message)
    def empty: LayerExpr = '{ZLayer.succeed(())}
    def composeH(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
      lhs match {
        case '{$lhs: ZLayer[i, e, o]} =>
          rhs match {
            case '{$rhs: ZLayer[i2, e2, o2]} =>
              '{$lhs.++($rhs)}
          }
      }

    def composeV(lhs: LayerExpr, rhs: LayerExpr): LayerExpr =
      lhs match {
        case '{$lhs: ZLayer[i, e, o]} =>
          rhs match {
            case '{$rhs: ZLayer[i2, e2, o2]} =>
              '{$lhs >>> $rhs.asInstanceOf[ZLayer[o,e2,o2]]}
          }
      }

    ZLayerExprBuilder(
      Graph(nodes, _ <:< _),
      renderTypeRepr,
      renderExpr,
      compileError,
      compileWarning,
      empty,
      composeH,
      composeV
      )
  }
}

