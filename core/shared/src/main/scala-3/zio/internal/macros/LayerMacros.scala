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
  def constructStaticLayer[R0: Type, R: Type, E: Type](
    layers: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes): Expr[ZLayer[R0, E, R]] =
    layers match {
      case Varargs(layers) =>
        LayerMacroUtils.constructStaticLayer[R0, R, E](layers, ProvideMethod.Provide)
    }

  def constructDynamicLayer[R: Type, E: Type](
    layers: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes): Expr[ZLayer[_, _, R]] =
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
    val layerExpr = constructStaticLayer[R0, R, E](layer)
    '{ $zio.provideLayer($layerExpr) }
  }

  def provideDynamicImpl[R: Type, E: Type, A: Type](
    zio: Expr[ZIO[R, E, A]],
    layer: Expr[Seq[ZLayer[_, E, _]]]
  )(using
    Quotes
  ): Expr[ZIO[_, _, _]] = {
    val layerExpr = constructDynamicLayer[R, E](layer)
    layerExpr match {
      case '{ $layer: ZLayer[in, e, out] } => {
        // This is very weird cast - compiler knows that layerExpr is ZLayer[R, _, _] so it should match
        // but without it I get
        //  [error]    |   Your effect requires a service that is not in the environment.
        //  [error]    |   Please provide a layer for the following type:
        //  [error]    |
        //  [error]    |     1. scala.Nothing
        val z = zio.asExprOf[ZIO[out, E, A]]
        '{ $z.provideLayer($layer) }
      }
    }

  }

  def runWithImpl[R: Type, E: Type](
    layer: Expr[ZLayer[R, E, Unit]],
    deps: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes) = {
    val layerExpr = constructStaticLayer[Any, R, E](deps)
    '{ ZIO.scoped($layer.build).provideLayer($layerExpr).unit }
  }

}
