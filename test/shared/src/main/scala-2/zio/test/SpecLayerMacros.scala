package zio.test

import zio._
import zio.internal.macros.DepsMacroUtils
import zio.test.environment.TestEnvironment

import scala.reflect.macros.blackbox

class SpecDepsMacros(val c: blackbox.Context) extends DepsMacroUtils {
  def injectSharedImpl[R: c.WeakTypeTag, E, A](
    deps: c.Expr[ZDeps[_, E, _]]*
  ): c.Expr[Spec[Any, E, A]] =
    injectBaseImpl[Spec, Any, R, E, A](deps, "provideDepsShared")

  def injectCustomSharedImpl[R: c.WeakTypeTag, E, A](
    deps: c.Expr[ZDeps[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] =
    injectBaseImpl[Spec, TestEnvironment, R, E, A](deps, "provideDepsShared")

  def injectSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    deps: c.Expr[ZDeps[_, E, _]]*
  ): c.Expr[Spec[R0, E, A]] =
    injectBaseImpl[Spec, R0, R, E, A](deps, "provideDepsShared")
}
