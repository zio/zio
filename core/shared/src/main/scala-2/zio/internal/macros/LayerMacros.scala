package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

import scala.reflect.macros.blackbox

private[zio] class LayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def provideImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[Any, E, A]] =
    provideBaseImpl[F, Any, R, E, A](layer, "manuallyProvide")

  def provideSomeImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[R0, E, A]] = {
    assertEnvIsNotNothing[R0]()
    provideBaseImpl[F, R0, R, E, A](layer, "manuallyProvide")
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
      val errorMessage =
        s"""
${"  ZLayer Wiring Error  ".red.bold.inverted}
        
You must provide a type to ${"provideSome".cyan.bold} (e.g. ${"foo.provideSome".cyan.bold}${"[UserService with Config".red.bold.underlined}${"(AnotherService.live)".cyan.bold})

This type represents the services you are ${"not".underlined} currently providing, leaving them in the environment until later.

"""
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

}

private[zio] object MacroUnitTestUtils {
  def getRequirements[R]: List[String] = macro LayerMacros.debugGetRequirements[R]

  def showTree(any: Any): String = macro LayerMacros.debugShowTree
}
