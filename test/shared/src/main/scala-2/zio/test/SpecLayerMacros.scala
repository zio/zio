package zio.test

import zio._
import zio.internal.macros.LayerMacroUtils

import scala.reflect.macros.blackbox

class SpecLayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  def provideSharedImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[Any, E, A]] =
    provideBaseImpl[Spec, Any, R, E, A](layers, "provideShared")

  def provideCustomSharedImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] =
    provideBaseImpl[Spec, TestEnvironment, R, E, A](layers, "provideShared")

  def provideSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[R0, E, A]] =
    provideBaseImpl[Spec, R0, R, E, A](layers, "provideSomeShared")
}
