package zio.test

import zio._
import zio.internal.macros.{LayerMacroUtils, ProvideMethod}
import zio.internal.macros.ProvideMethod.Provide

import scala.reflect.macros.blackbox

class SpecLayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  def provideSharedImpl[R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  )(ev: c.Tree): c.Expr[Spec[Any, E, A]] =
    provideBaseImpl[Spec, Any, R, E, A](layer, "provideLayerShared", ProvideMethod.Provide)(ev)

  def provideCustomSharedImpl[R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  )(ev: c.Tree): c.Expr[Spec[TestEnvironment, E, A]] =
    provideBaseImpl[Spec, TestEnvironment, R, E, A](layer, "provideLayerShared", ProvideMethod.ProvideCustom)(ev)

  def provideSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  )(ev: c.Tree): c.Expr[Spec[R0, E, A]] =
    provideBaseImpl[Spec, R0, R, E, A](layer, "provideLayerShared", ProvideMethod.ProvideSome)(ev)
}
