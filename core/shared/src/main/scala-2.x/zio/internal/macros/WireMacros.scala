package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio.internal.macros.StringUtils.StringOps
import zio.{Has, ZLayer}

import scala.reflect.macros.blackbox

final class WireMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def wireImpl[
    E,
    R0: c.WeakTypeTag,
    R <: Has[_]: c.WeakTypeTag
  ](layers: c.Expr[ZLayer[_, E, _]]*)(
    dummyKRemainder: c.Expr[DummyK[R0]],
    dummyK: c.Expr[DummyK[R]]
  ): c.Expr[ZLayer[R0, E, R]] = {
    val _ = (dummyK, dummyKRemainder)
    assertEnvIsNotNothing[R]()
    assertProperVarArgs(layers)

    val deferredRequirements = getRequirements[R0]
    val requirements         = getRequirements[R] diff deferredRequirements

    val deferredLayer =
      if (deferredRequirements.nonEmpty) Seq(Node(List.empty, deferredRequirements, reify(ZLayer.requires[R0])))
      else Nil

    val nodes = (deferredLayer ++ layers.map(getNode)).toList

    buildMemoizedLayer(generateExprGraph(nodes), deferredRequirements ++ requirements)
      .asInstanceOf[c.Expr[ZLayer[R0, E, R]]]
  }

  def wireDebugImpl[
    E,
    R0: c.WeakTypeTag,
    R <: Has[_]: c.WeakTypeTag
  ](layers: c.Expr[ZLayer[_, E, _]]*)(
    dummyKRemainder: c.Expr[DummyK[R0]],
    dummyK: c.Expr[DummyK[R]]
  ): c.Expr[ZLayer[R0, E, R]] = {
    val _ = (dummyK, dummyKRemainder)
    assertEnvIsNotNothing[R]()
    assertProperVarArgs(layers)

    val deferredRequirements = getRequirements[R0]
    val requirements         = getRequirements[R] diff deferredRequirements

    val deferredLayer =
      if (deferredRequirements.isEmpty) List.empty
      else List(Node(List.empty, deferredRequirements, reify(ZLayer.requires[R0])))
    val nodes = deferredLayer ++ layers.map(getNode)

    val graph = generateExprGraph(nodes)
    graph.buildLayerFor(requirements)

    val graphString: String = {
      eitherToOption(
        graph.graph
          .map(layer => RenderedGraph(layer.showTree))
          .buildComplete(requirements)
      ).get
        .fold[RenderedGraph](RenderedGraph.Row(List.empty), identity, _ ++ _, _ >>> _)
        .render
    }

    val maxWidth = graphString.maxLineWidth
    val title    = "  ZLayer Wiring Graph  ".yellow.bold.inverted
    val adjust   = (maxWidth - title.length) / 2
    val rendered = "\n" + (" " * adjust) + title + "\n\n" + graphString + "\n\n"

    c.abort(c.enclosingPosition, rendered)
  }

  /**
   * Scala 2.11 doesn't have `Either.toOption`
   */
  private def eitherToOption[A](either: Either[_, A]): Option[A] = either match {
    case Left(_)      => None
    case Right(value) => Some(value)
  }

  /**
   * Ensures the macro has been annotated with the intended result type.
   * The macro will not behave correctly otherwise.
   */
  private def assertEnvIsNotNothing[Out <: Has[_]: c.WeakTypeTag](): Unit = {
    val outType     = weakTypeOf[Out]
    val nothingType = weakTypeOf[Nothing]
    if (outType == nothingType) {
      val errorMessage =
        s"""
${"  ZLayer Wiring Error  ".red.bold.inverted}
        
You must provide a type to ${"wire".cyan.bold} (e.g. ${"ZLayer.wire".cyan.bold}${"[A with B]".cyan.bold.underlined}${"(A.live, B.live)".cyan.bold})

"""
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

}
