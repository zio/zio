package zio.test

import zio.internal.macros.LayerMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._
import zio.test.environment.TestEnvironment

object SpecLayerMacros {
  def injectImpl[R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[Any,E,T]] = {
    val expr = buildLayerFor[R](layers)
    '{$spec.provideLayerManual($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def provideCustomLayerImpl[R <: Has[?], E, T]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using ctx: Quotes, tr: Type[R], te: Type[E], tt: Type[T]): Expr[Spec[TestEnvironment,E,T]] = {
    import ctx.reflect._

    val ZEnvRequirements = getRequirements[TestEnvironment]("TestEnvironment")
    val requirements     = getRequirements[R]("R")

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{TestEnvironment.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = buildMemoizedLayer(ctx)(ZLayerExprBuilder.fromNodes(ctx)(nodes), requirements)

    '{$spec.asInstanceOf[Spec[Has[Unit], E, T]].provideLayerManual(TestEnvironment.any >>> $expr.asInstanceOf[ZLayer[TestEnvironment, E, Has[Unit]]])}
  }

  def injectSharedImpl[R: Type, E: Type, T: Type]
    (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[Any,E,T]] = {
    val expr = buildLayerFor[R](layers)
    '{$spec.provideLayerManualShared($expr.asInstanceOf[ZLayer[Any, E, R]])}
  }

  def injectCustomSharedImpl[R <: Has[?], E, T]
  (spec: Expr[Spec[R,E,T]], layers: Expr[Seq[ZLayer[_,E,_]]])(using ctx: Quotes, tr: Type[R], te: Type[E], tt: Type[T]): Expr[Spec[TestEnvironment,E,T]] = {
    import ctx.reflect._

    val ZEnvRequirements = getRequirements[TestEnvironment]("TestEnvironment")
    val requirements     = getRequirements[R]("R")

    val zEnvLayer = Node(List.empty, ZEnvRequirements, '{TestEnvironment.any})
    val nodes     = (zEnvLayer +: getNodes(layers)).toList

    val expr = buildMemoizedLayer(ctx)(ZLayerExprBuilder.fromNodes(ctx)(nodes), requirements)

    '{$spec.asInstanceOf[Spec[Has[Unit], E, T]]
      .provideLayerManualShared(TestEnvironment.any >>> $expr.asInstanceOf[ZLayer[TestEnvironment, E, Has[Unit]]])}
  }
}
