package zio.test

import zio.internal.macros.LayerMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._

object SpecLayerMacros {
  def provideImpl[R0: Type, R: Type, E: Type](spec: Expr[Spec[R, E]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[Spec[R0, E]] = {
    val expr = LayerMacros.constructStaticLayer[R0, R, E](layer)
    '{ $spec.provideLayer($expr) }
  }

  def provideAutoImpl[R: Type, E: Type](spec: Expr[Spec[R, E]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[Spec[_, E]] = {
    val layerExpr = LayerMacros.constructDynamicLayer[R, E](layer)
    // https://github.com/scala/scala3/issues/22886
    layerExpr match {
      case '{ $layer: ZLayer[in, e, out] } =>
        '{ $spec.provideLayer($layer) }
    }
  }

  def provideSharedImpl[R0: Type, R: Type, E: Type](spec: Expr[Spec[R, E]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[Spec[R0, E]] = {
    val expr = LayerMacros.constructStaticLayer[R0, R, E](layer)
    '{ $spec.provideLayerShared($expr) }
  }

  def provideSharedAutoImpl[R: Type, E: Type](spec: Expr[Spec[R, E]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[Spec[_, E]] = {
    val layerExpr = LayerMacros.constructDynamicLayer[R, E](layer)
    // https://github.com/scala/scala3/issues/22886
    layerExpr match {
      case '{ $layer: ZLayer[in, e, out] } =>
        '{ $spec.provideLayerShared($layer) }
    }
  }
}
