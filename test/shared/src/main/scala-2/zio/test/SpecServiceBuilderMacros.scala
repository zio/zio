package zio.test

import zio._
import zio.internal.macros.ServiceBuilderMacroUtils

import scala.reflect.macros.blackbox

class SpecServiceBuilderMacros(val c: blackbox.Context) extends ServiceBuilderMacroUtils {
  def injectSharedImpl[R: c.WeakTypeTag, E, A](
    serviceBuilder: c.Expr[ZServiceBuilder[_, E, _]]*
  ): c.Expr[Spec[Any, E, A]] =
    injectBaseImpl[Spec, Any, R, E, A](serviceBuilder, "provideShared")

  def injectCustomSharedImpl[R: c.WeakTypeTag, E, A](
    serviceBuilder: c.Expr[ZServiceBuilder[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] =
    injectBaseImpl[Spec, TestEnvironment, R, E, A](serviceBuilder, "provideShared")

  def injectSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    serviceBuilder: c.Expr[ZServiceBuilder[_, E, _]]*
  ): c.Expr[Spec[R0, E, A]] =
    injectBaseImpl[Spec, R0, R, E, A](serviceBuilder, "provideShared")
}
