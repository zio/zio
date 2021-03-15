package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps
import zio.internal.macros.StringUtils.StringOps

import scala.reflect.macros.blackbox

final class WireMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def fromAutoImpl[
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

    val deferredLayer = Node(List.empty, deferredRequirements, reify(ZLayer.requires[R0]))
    val nodes         = (deferredLayer +: layers.map(getNode)).toList

    buildMemoizedLayer(generateExprGraph(nodes), requirements)
      .asInstanceOf[c.Expr[ZLayer[R0, E, R]]]
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
        
You must provide a type to ${"wire".white} (e.g. ${"ZLayer.wire".white}${"[A with B]".yellow.underlined}${"(A.live, B.live)".white})

"""
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

}
