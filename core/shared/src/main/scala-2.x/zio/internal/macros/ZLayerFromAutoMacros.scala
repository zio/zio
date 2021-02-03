package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps
import zio.internal.macros.StringUtils.StringOps

import scala.reflect.macros.blackbox

final class ZLayerFromAutoMacros(val c: blackbox.Context) extends AutoLayerMacroUtils {
  import c.universe._

  def fromAutoImpl[
    E,
    R <: Has[_]: c.WeakTypeTag
  ](layers: c.Expr[ZLayer[_, E, _]]*)(
    dummyK: c.Expr[DummyK[R]]
  ): c.Expr[ZLayer[Any, E, R]] = {
    val _ = dummyK
    assertEnvIsNotNothing[R]()
    assertProperVarArgs(layers)
    generateExprGraph(layers)
      .buildLayerFor(getRequirements[R])
      .asInstanceOf[c.Expr[ZLayer[Any, E, R]]]
  }

  def fromAutoDebugImpl[
    E,
    Out <: Has[_]: c.WeakTypeTag
  ](layers: c.Expr[ZLayer[_, E, _]]*)(
    dummyK: c.Expr[DummyK[Out]]
  ): c.Expr[ZLayer[Any, E, Out]] = {
    val _ = dummyK
    assertEnvIsNotNothing[Out]()
    assertProperVarArgs(layers)
    val graph        = generateExprGraph(layers)
    val requirements = getRequirements[Out]
    graph.buildLayerFor(requirements)

    val graphString: String = eitherToOption(
      graph.graph
        .map(layer => RenderedGraph(layer.showTree))
        .buildComplete(requirements)
    ).get.fold(RenderedGraph.Row(List.empty), _ ++ _, _ >>> _).render

    val maxWidth = graphString.maxLineWidth
    val title    = "Layer Graph Visualization"
    val adjust   = (maxWidth - title.length) / 2

    val rendered = "\n" + (" " * adjust) + title.yellow.underlined + "\n\n" + graphString + "\n\n"

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
${"ZLayer Auto Assemble".yellow.underlined}
        
You must provide a type to ${"fromAuto".white} (e.g. ${"ZLayer.fromAuto".white}${"[A with B]".yellow.underlined}${"(A.live, B.live)".white})

"""
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

}
