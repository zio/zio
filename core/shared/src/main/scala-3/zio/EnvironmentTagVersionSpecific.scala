package zio

import scala.quoted.*

trait EnvironmentTagVersionSpecific {
  implicit inline def materialize[A]: EnvironmentTag[A] =
    ${ EnvironmentTagMacros.materialize[A] }
}

private object EnvironmentTagMacros {
  def materialize[A: Type](using Quotes): Expr[EnvironmentTag[A]] = {
    import quotes.reflect.*
    TypeRepr.of[A].dealias match {
      case tpe if tpe.typeSymbol.isTypeParam =>
        Expr.summon[EnvironmentTag[A]].getOrElse(
          report.errorAndAbort( s"Cannot find implicit EnvironmentTag[${tpe.show}]" )
        )
      case _ =>
        '{ new EnvironmentTag[A] { def tag: LightTypeTag = Tag[A].tag } }
    }
  }
}