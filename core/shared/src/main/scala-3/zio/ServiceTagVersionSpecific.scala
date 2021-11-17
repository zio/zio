package zio

import scala.quoted.*

trait ServiceTagVersionSpecific {
  implicit inline def materialize[A]: ServiceTag[A] =
    ${ ServiceTagMacros.materialize[A] }
}

private object ServiceTagMacros {
  def materialize[A: Type](using Quotes): Expr[ServiceTag[A]] = {
    import quotes.reflect.*
    TypeRepr.of[A].dealias match {
      case tpe if tpe.typeSymbol.isTypeParam =>
        Expr.summon[ServiceTag[A]].getOrElse(
          report.errorAndAbort( s"Cannot find implicit ServiceTag[${tpe.show}]" )
        )
      case AndType(_, _) =>
        report.errorAndAbort(s"You must not use an intersection type, yet have provided ${Type.show[A]}")
      case _ =>
        '{ new ServiceTag[A] { def tag: LightTypeTag = Tag[A].tag } }
    }
  }
}