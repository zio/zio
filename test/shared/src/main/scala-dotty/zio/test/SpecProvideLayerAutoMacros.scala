package zio.test

import zio.internal.macros.AutoLayerMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._
import zio.test.environment.TestEnvironment

object SpecProvideLayerAutoMacros {
  def provideLayerImpl[R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[Any,E,T]] = {
    val expr = buildLayerFor[R](layers)
    '{$spec.provideLayerManual($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def provideCustomLayerImpl[R <: Has[?], E, T]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes, Type[R], Type[E], Type[T]): Expr[Spec[TestEnvironment,E,T]] = {
    val ZEnvRequirements = getRequirements[TestEnvironment]
    val requirements     = getRequirements[R]

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{TestEnvironment.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = buildMemoizedLayer(ZLayerExprBuilder(nodes), requirements)

    '{$spec.asInstanceOf[Spec[Has[Unit], E, T]].provideLayerManual(TestEnvironment.any >>> $expr.asInstanceOf[ZLayer[TestEnvironment, E, Has[Unit]]])}
  }

  def provideLayerManualSharedAutoImpl[R: Type, E: Type, T: Type]
    (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[Any,E,T]] = {
    val expr = buildLayerFor[R](layers)
    '{$spec.provideLayerManualShared($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def provideCustomLayerManualSharedAutoImpl[R <: Has[?], E, T]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes, Type[R], Type[E], Type[T]): Expr[Spec[TestEnvironment,E,T]] = {
    val ZEnvRequirements = getRequirements[TestEnvironment]
    val requirements     = getRequirements[R]

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{TestEnvironment.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = buildMemoizedLayer(ZLayerExprBuilder(nodes), requirements)

    '{$spec.asInstanceOf[Spec[Has[Unit], E, T]].provideLayerManualShared(TestEnvironment.any >>> $expr.asInstanceOf[ZLayer[TestEnvironment, E, Has[Unit]]])}
  }
}
