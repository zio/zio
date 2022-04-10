package zio.test

import zio._
import zio.internal.macros.{LayerMacroUtils, ProvideMethod}
import zio.internal.macros.ProvideMethod.Provide

import scala.reflect.macros.blackbox

class SpecLayerMacros(val c: blackbox.Context) extends LayerMacroUtils {

  private type ZSpec[-R, +E, +T] = Spec[R, E]

  def provideSharedImpl[R: c.WeakTypeTag, E](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[Any, E]] =
    provideBaseImpl[ZSpec, Any, R, E, TestSuccess](layer, "provideLayerShared", ProvideMethod.Provide)

  def provideCustomSharedImpl[R: c.WeakTypeTag, E](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E]] =
    provideBaseImpl[ZSpec, TestEnvironment, R, E, TestSuccess](layer, "provideLayerShared", ProvideMethod.ProvideCustom)

  def provideSomeSharedImpl[R0: c.WeakTypeTag, R: c.WeakTypeTag, E](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[R0, E]] =
    provideBaseImpl[ZSpec, R0, R, E, TestSuccess](layer, "provideLayerShared", ProvideMethod.ProvideSome)
}
