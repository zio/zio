package zio

import scala.quoted.*

trait TagVersionSpecific {
  implicit transparent inline def materialize[A]: Tag[A] =
    ${ TagMacros.materialize[A] }
}

private object TagMacros {

  def materialize[A: Type](using Quotes): Expr[Tag[A]] = {
    import quotes.reflect.*
    TypeRepr.of[A].dealias match {
      case tpe if tpe.typeSymbol.isTypeParam || tpe.typeSymbol.isAbstractType =>
        Expr.summon[Tag[A]] match {
          case Some(tag) => tag
          case None => report.errorAndAbort( s"Cannot find implicit Tag[${tpe.show}]" )
        }
      case AndType(_, _) =>
        report.errorAndAbort(s"You must not use an intersection type, yet have provided ${Type.show[A]}")
      case tpe =>
        '{
          val tag0 = EnvironmentTag[A]
          new Tag[A] {
            def tag: LightTypeTag =  tag0.tag
            def closestClass: Class[_] = tag0.closestClass
          }
        }
    }
  }
}