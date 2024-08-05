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
  def constructLayer[R0: Type, R: Type, E: Type](
    layers: Expr[Seq[ZLayer[_, E, _]]]
  )(using Quotes): Expr[ZLayer[R0, E, R]] =
    layers match {
      case Varargs(layers) =>
        LayerMacroUtils.constructLayer[R0, R, E](layers, ProvideMethod.Provide)
    }

  def provideImpl[R0: Type, R: Type, E: Type, A: Type](zio: Expr[ZIO[R, E, A]], layer: Expr[Seq[ZLayer[_, E, _]]])(using
    Quotes
  ): Expr[ZIO[R0, E, A]] = {
    val layerExpr = constructLayer[R0, R, E](layer)
    '{ $zio.provideLayer($layerExpr) }
  }
}
