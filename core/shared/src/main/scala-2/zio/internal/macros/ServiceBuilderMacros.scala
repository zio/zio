package zio.internal.macros

import zio._
import zio.internal.ansi.AnsiStringOps

import scala.reflect.macros.blackbox

private[zio] class ServiceBuilderMacros(val c: blackbox.Context) extends ServiceBuilderMacroUtils {
  import c.universe._

  def injectImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    serviceBuilder: c.Expr[ZServiceBuilder[_, E, _]]*
  ): c.Expr[F[Any, E, A]] =
    injectBaseImpl[F, Any, R, E, A](serviceBuilder, "provideServices")

  def injectSomeImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    serviceBuilder: c.Expr[ZServiceBuilder[_, E, _]]*
  ): c.Expr[F[R0, E, A]] = {
    assertEnvIsNotNothing[R0]()
    injectBaseImpl[F, R0, R, E, A](serviceBuilder, "provideServices")
  }

  def materializeIsNotIntersection[A: c.WeakTypeTag]: c.Expr[IsNotIntersection[A]] = {
    val tpe = c.weakTypeOf[A]
//    throw new Error(s"HEY ${tpe}")
    tpe.dealias match {
      case RefinedType(_, _) =>
        c.abort(c.enclosingPosition, s"You must not use an intersection type, yet have provided: $tpe")
      case _ =>
        c.Expr[IsNotIntersection[A]](q"new _root_.zio.IsNotIntersection[$tpe] {}")
    }
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
${"  ZServiceBuilder Wiring Error  ".red.bold.inverted}
        
You must provide a type to ${"injectSome".cyan.bold} (e.g. ${"foo.injectSome".cyan.bold}${"[Has[UserService] with Has[Config]".red.bold.underlined}${"(AnotherService.live)".cyan.bold})

This type represents the services you are ${"not".underlined} currently injecting, leaving them in the environment until later.

"""
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

}

private[zio] object MacroUnitTestUtils {
  def getRequirements[R]: List[String] = macro ServiceBuilderMacros.debugGetRequirements[R]

  def showTree(any: Any): String = macro ServiceBuilderMacros.debugShowTree
}
