package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

import scala.reflect.macros.blackbox

private[zio] class LayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def injectImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[Any, E, A]] =
    injectBaseImpl[F, Any, R, E, A](layer, "provide")

  def injectSomeImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[R0, E, A]] = {
    assertEnvIsNotNothing[R0]()
    injectBaseImpl[F, R0, R, E, A](layer, "provide")
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
        
You must provide a type to ${"injectSome".cyan.bold} (e.g. ${"foo.injectSome".cyan.bold}${"[UserService with Config".red.bold.underlined}${"(AnotherService.live)".cyan.bold})

This type represents the services you are ${"not".underlined} currently injecting, leaving them in the environment until later.

"""
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

}

private[zio] object MacroUnitTestUtils {
  def getRequirements[R]: List[String] = macro LayerMacros.debugGetRequirements[R]

  def showTree(any: Any): String = macro LayerMacros.debugShowTree
}
