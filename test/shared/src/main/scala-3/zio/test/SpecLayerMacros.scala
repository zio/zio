package zio.test

import zio.internal.macros.LayerMacroUtils._
import scala.quoted._
import zio.internal.macros._
import zio._

object SpecLayerMacros {
  def provideImpl[R0: Type, R: Type, E: Type](spec: Expr[Spec[R, E]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[Spec[R0, E]] = {
    val expr = LayerMacros.constructStaticProvideLayer[R0, R, E](layer)
    '{ $spec.provideLayer($expr) }
  }

  def provideSomeImpl[R0: Type, R: Type, E: Type](spec: Expr[Spec[R, E]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[Spec[R0, E]] = {
    val expr = LayerMacros.constructStaticProvideSomeLayer[R0, R, E](layer)
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
    import quotes.reflect._
    val expr = LayerMacros.constructStaticProvideLayer[R0, R, E](layer)
    '{ $spec.provideLayerShared($expr) }
  }

  def provideSomeSharedImpl[R0: Type, R: Type, E: Type](spec: Expr[Spec[R, E]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[Spec[R0, E]] = {
    import quotes.reflect._
    val layerExpr: Expr[ZLayer[R0, E, ?]] = LayerMacros.constructStaticProvideSomeSharedLayer[R0, R, E](layer)
    layerExpr match {
      case '{ $layer: ZLayer[in, e, out] } =>
        '{

          /**
           * Contract of [[zio.internal.macros.LayerBuilder.build]] ensures this
           */
          given <:<[R0 & out, R] = null
          $spec.provideSomeLayerShared[R0]($layer)
        }
    }
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
