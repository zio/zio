package zio.test

import zio.internal.macros.ServiceBuilderMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._

object SpecServiceBuilderMacros {
  def injectImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R, E, T]], serviceBuilder: Expr[Seq[ZServiceBuilder[_,E,_]]])(using Quotes): Expr[Spec[R0, E, T]] = {
    val expr = ServiceBuilderMacros.fromAutoImpl[R0, R, E](serviceBuilder).asInstanceOf[Expr[ZServiceBuilder[R0, E, R]]]
    '{$spec.provide($expr)}
  }

  def injectSharedImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], serviceBuilder: Expr[Seq[ZServiceBuilder[_,E,_]]])(using Quotes): Expr[Spec[R0,E,T]] = {
    val expr = ServiceBuilderMacros.fromAutoImpl[R0, R, E](serviceBuilder)
    '{$spec.provideShared($expr)}
  }
}
