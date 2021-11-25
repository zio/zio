package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio.ZLayer
import zio.internal.TerminalRendering

import scala.reflect.macros.blackbox

final class ZLayerMakeMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def makeImpl[
    E,
    R0: c.WeakTypeTag,
    R: c.WeakTypeTag
  ](layer: c.Expr[ZLayer[_, E, _]]*)(
    dummyKRemainder: c.Expr[DummyK[R0]],
    dummyK: c.Expr[DummyK[R]]
  ): c.Expr[ZLayer[R0, E, R]] = {
    val _ = (dummyK, dummyKRemainder)
    assertEnvIsNotNothing[R]()
    constructLayer[R0, R, E](layer)
  }

  /**
   * Ensures the macro has been annotated with the intended result type. The
   * macro will not behave correctly otherwise.
   */
  private def assertEnvIsNotNothing[R: c.WeakTypeTag](): Unit = {
    val outType     = weakTypeOf[R]
    val nothingType = weakTypeOf[Nothing]
    if (outType == nothingType) {
      val errorMessage = TerminalRendering.provideSomeNothingEnvError
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

}
