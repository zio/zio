package zio.test

import zio.internal.macros.ProviderMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._

object SpecProviderMacros {
  def injectImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R, E, T]], provider: Expr[Seq[ZProvider[_,E,_]]])(using Quotes): Expr[Spec[R0, E, T]] = {
    val expr = ProviderMacros.fromAutoImpl[R0, R, E](provider).asInstanceOf[Expr[ZProvider[R0, E, R]]]
    '{$spec.provide($expr)}
  }

  def injectSharedImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], provider: Expr[Seq[ZProvider[_,E,_]]])(using Quotes): Expr[Spec[R0,E,T]] = {
    val expr = ProviderMacros.fromAutoImpl[R0, R, E](provider)
    '{$spec.provideShared($expr)}
  }
}
