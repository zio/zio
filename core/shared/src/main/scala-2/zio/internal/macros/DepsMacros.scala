package zio.internal.macros

import zio._

import scala.reflect.macros.blackbox

private[zio] class DepsMacros(val c: blackbox.Context) extends DepsMacroUtils {
  import c.universe._

  def injectImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    deps: c.Expr[ZDeps[_, E, _]]*
  ): c.Expr[F[Any, E, A]] =
    injectBaseImpl[F, Any, R, E, A](deps, "provideDeps")

  def injectSomeImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    deps: c.Expr[ZDeps[_, E, _]]*
  ): c.Expr[F[R0, E, A]] =
    injectBaseImpl[F, R0, R, E, A](deps, "provideDeps")

  def debugGetRequirements[R: c.WeakTypeTag]: c.Expr[List[String]] =
    c.Expr[List[String]](q"${getRequirements[R]}")

  def debugShowTree(any: c.Tree): c.Expr[String] = {
    val string = CleanCodePrinter.show(c)(any)
    c.Expr[String](q"$string")
  }
}

private[zio] object MacroUnitTestUtils {
  def getRequirements[R]: List[String] = macro DepsMacros.debugGetRequirements[R]

  def showTree(any: Any): String = macro DepsMacros.debugShowTree
}
