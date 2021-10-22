package zio.test

import zio.internal.macros.DepsMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._
import zio.test.environment.TestEnvironment

object SpecDepsMacros {
  def injectImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R, E, T]], deps: Expr[Seq[ZDeps[_,E,_]]])(using Quotes): Expr[Spec[R0, E, T]] = {
    val expr = DepsMacros.fromAutoImpl[R0, R, E](deps).asInstanceOf[Expr[ZDeps[R0, E, R]]]
    '{$spec.provideDeps($expr)}
  }

  def injectSharedImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], deps: Expr[Seq[ZDeps[_,E,_]]])(using Quotes): Expr[Spec[R0,E,T]] = {
    val expr = DepsMacros.fromAutoImpl[R0, R, E](deps)
    '{$spec.provideDepsShared($expr)}
  }
}
