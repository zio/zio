package zio.test

import zio._
import zio.internal.macros.ProviderMacroUtils

import scala.reflect.macros.blackbox

class SpecProviderMacros(val c: blackbox.Context) extends ProviderMacroUtils {
  def injectSharedImpl[R: c.WeakTypeTag, E, A](
    provider: c.Expr[ZProvider[_, E, _]]*
  ): c.Expr[Spec[Any, E, A]] =
    injectBaseImpl[Spec, Any, R, E, A](provider, "provideShared")

  def injectCustomSharedImpl[R: c.WeakTypeTag, E, A](
    provider: c.Expr[ZProvider[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] =
    injectBaseImpl[Spec, TestEnvironment, R, E, A](provider, "provideShared")

  def injectSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    provider: c.Expr[ZProvider[_, E, _]]*
  ): c.Expr[Spec[R0, E, A]] =
    injectBaseImpl[Spec, R0, R, E, A](provider, "provideShared")
}
