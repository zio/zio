package zio.internal.macros

import zio._
import zio.internal.TerminalRendering
import zio.internal.ansi.AnsiStringOps

import scala.reflect.macros.blackbox

private[zio] class LayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def provideImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[Any, E, A]] =
    provideBaseImpl[F, Any, R, E, A](layers, "provide")

  def provideSomeImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[R0, E, A]] = {
    assertEnvIsNotNothing[R0]()
    provideBaseImpl[F, R0, R, E, A](layers, "provide")
  }

  def debugGetRequirements[R: c.WeakTypeTag]: c.Expr[List[String]] =
    c.Expr[List[String]](q"${getRequirements[R]}")

  def debugShowTree(any: c.Tree): c.Expr[String] = {
    val string = CleanCodePrinter.show(c)(any)
    c.Expr[String](q"$string")
  }

  /**
   * Ensures the macro has been annotated with the intended result type. The
   * macro will not behave correctly otherwise.
   */
  private def assertEnvIsNotNothing[R: c.WeakTypeTag](): Unit = {
    val outType     = weakTypeOf[R]
    val nothingType = weakTypeOf[Nothing]
    if (outType =:= nothingType) {
      val message: String = TerminalRendering.provideSomeNothingEnvError
      c.abort(c.enclosingPosition, message)
    }
  }

}

private[zio] object MacroUnitTestUtils {
  def getRequirements[R]: List[String] = macro LayerMacros.debugGetRequirements[R]

  def showTree(any: Any): String = macro LayerMacros.debugShowTree
}
