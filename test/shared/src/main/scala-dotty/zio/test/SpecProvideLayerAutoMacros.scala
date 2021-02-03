package zio.test

import zio.internal.macros.AutoLayerMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._
import zio.test.environment.TestEnvironment

object SpecProvideLayerAutoMacros {
  def provideLayerAutoImpl[R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[Any,E,T]] = {
    val expr = buildLayerFor[R](layers)
    '{$spec.provideLayer($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def provideCustomLayerAutoImpl[R <: Has[?], E, T]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes, Type[R], Type[E], Type[T]): Expr[Spec[TestEnvironment,E,T]] = {
    val ZEnvRequirements = intersectionTypes[TestEnvironment]
    val requirements     = intersectionTypes[R] 

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{TestEnvironment.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = generateExprGraph(nodes).buildLayerFor(requirements)

    '{$spec.asInstanceOf[Spec[Has[Unit], E, T]].provideLayer(TestEnvironment.any >>> $expr.asInstanceOf[ZLayer[TestEnvironment, E, Has[Unit]]])}
  }

  def provideLayerSharedAutoImpl[R: Type, E: Type, T: Type]
    (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[Any,E,T]] = {
    val expr = buildLayerFor[R](layers)
    '{$spec.provideLayerShared($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def provideCustomLayerSharedAutoImpl[R <: Has[?], E, T]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes, Type[R], Type[E], Type[T]): Expr[Spec[TestEnvironment,E,T]] = {
    val ZEnvRequirements = intersectionTypes[TestEnvironment]
    val requirements     = intersectionTypes[R] 

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{TestEnvironment.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = generateExprGraph(nodes).buildLayerFor(requirements)

    '{$spec.asInstanceOf[Spec[Has[Unit], E, T]].provideLayerShared(TestEnvironment.any >>> $expr.asInstanceOf[ZLayer[TestEnvironment, E, Has[Unit]]])}
  }
}
