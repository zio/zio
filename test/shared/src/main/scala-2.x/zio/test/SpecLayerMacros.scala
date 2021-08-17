package zio.test

import zio._
import zio.internal.macros.LayerMacroUtils
import zio.test.environment.TestEnvironment

import scala.reflect.macros.blackbox

class SpecLayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  def injectSharedImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[Any, E, A]] =
    injectBaseImpl[Spec, R, E, A](layers, "provideLayerShared")

  def injectCustomSharedImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] =
    injectSomeBaseImpl[Spec, TestEnvironment, R, E, A](layers, "provideLayerShared")

  def injectSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[R0, E, A]] =
    injectSomeBaseImpl[Spec, R0, R, E, A](layers, "provideLayerShared")
}
