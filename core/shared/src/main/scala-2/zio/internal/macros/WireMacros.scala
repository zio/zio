package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
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
    constructLayer[R0, R, E](layers)
  }

  /**
   * Ensures the macro has been annotated with the intended result type. The
   * macro will not behave correctly otherwise.
   */
  private def assertEnvIsNotNothing[R <: Has[_]: c.WeakTypeTag](): Unit = {
    val outType     = weakTypeOf[R]
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
