package zio.test

import zio.internal.macros.LayerMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._
import zio.test.environment.TestEnvironment

object SpecLayerMacros {
  def injectImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R, E, T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[R0, E, T]] = {
    val expr = LayerMacros.fromAutoImpl[R0, R, E](layers).asInstanceOf[Expr[ZLayer[R0, E, R]]]
    '{$spec.provideLayer($expr)}
  }

  def injectSharedImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[R0,E,T]] = {
    val expr = LayerMacros.fromAutoImpl[R0, R, E](layers)
    '{$spec.provideLayerShared($expr)}
  }
}
