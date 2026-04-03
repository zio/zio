package zio.internal.macros

import zio.internal.ansi.AnsiStringOps
import zio._
import scala.quoted._
import scala.compiletime._
import zio.internal.macros.StringUtils.StringOps
import java.nio.charset.StandardCharsets
import java.util.Base64

import LayerMacroUtils._

object LayerMacros {
  def constructStaticProvideLayer[R0: Type, R: Type, E: Type](
    layers: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes): Expr[ZLayer[R0, E, R]] =
    layers match {
      case Varargs(layers) =>
        LayerMacroUtils.constructStaticLayer[R0, R, E](layers, ProvideMethod.Provide)
    }

  def constructStaticProvideSomeLayer[R0: Type, R: Type, E: Type](
    layers: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes): Expr[ZLayer[R0, E, R]] =
    layers match {
      case Varargs(layers) =>
        LayerMacroUtils.constructStaticLayer[R0, R, E](layers, ProvideMethod.ProvideSome)
    }

  def constructStaticProvideSomeSharedLayer[R0: Type, R: Type, E: Type](
    layers: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes): Expr[ZLayer[R0, E, _]] =
    layers match {
      case Varargs(layers) =>
        LayerMacroUtils.constructStaticSomeLayer[R0, R, E](layers, ProvideMethod.ProvideSomeShared)
    }

  def constructDynamicLayer[R: Type, E: Type](
    layers: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes): Expr[ZLayer[_, E, R]] =
    layers match {
      case Varargs(layers) =>
        LayerMacroUtils.constructDynamicLayer[R, E](layers, ProvideMethod.Provide)
    }

  def provideStaticImpl[R0: Type, R: Type, E: Type, A: Type](
    zio: Expr[ZIO[R, E, A]],
    layer: Expr[Seq[ZLayer[_, E, _]]]
  )(using
    Quotes
  ): Expr[ZIO[R0, E, A]] = {
    val layerExpr = constructStaticProvideLayer[R0, R, E](layer)
    '{ $zio.provideLayer($layerExpr) }
  }

  def provideSomeStaticImpl[R0: Type, R: Type, E: Type, A: Type](
    zio: Expr[ZIO[R, E, A]],
    layer: Expr[Seq[ZLayer[_, E, _]]]
  )(using
    Quotes
  ): Expr[ZIO[R0, E, A]] = {
    val layerExpr = constructStaticProvideSomeLayer[R0, R, E](layer)
    '{ $zio.provideLayer($layerExpr) }
  }

  def provideDynamicImpl[R: Type, E: Type, A: Type](
    zio: Expr[ZIO[R, E, A]],
    layer: Expr[Seq[ZLayer[_, E, _]]]
  )(using
    Quotes
  ): Expr[ZIO[_, E, A]] = {
    val layerExpr = constructDynamicLayer[R, E](layer)

    // https://github.com/scala/scala3/issues/22886
    layerExpr match {
      case '{ $layer: ZLayer[in, e, out] } =>
        '{ $zio.provideLayer($layer) }
    }
  }

  def runWithImpl[R: Type, E: Type](
    layer: Expr[ZLayer[R, E, Unit]],
    deps: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes) = {
    val layerExpr = constructStaticProvideLayer[Any, R, E](deps)
    '{ ZIO.scoped($layer.build).provideLayer($layerExpr).unit }
  }

}
