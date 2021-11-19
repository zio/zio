package zio.test

import zio._
import zio.internal.macros.LayerMacroUtils

import scala.reflect.macros.blackbox

class SpecLayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  def injectSharedImpl[R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[Any, E, A]] =
    injectBaseImpl[Spec, Any, R, E, A](layer, "provideShared")

  def injectCustomSharedImpl[R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] =
    injectBaseImpl[Spec, TestEnvironment, R, E, A](layer, "provideShared")

  def injectSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[R0, E, A]] =
    injectBaseImpl[Spec, R0, R, E, A](layer, "provideShared")
}
