package zio

import scala.quoted.*

trait ServiceTagVersionSpecific {
  implicit transparent inline def materialize[A]: ServiceTag[A] =
    ${ ServiceTagMacros.materialize[A] }
}

private object ServiceTagMacros {

  def materialize[A: Type](using Quotes): Expr[ServiceTag[A]] = {
    import quotes.reflect.*
    TypeRepr.of[A].dealias match {
      case tpe if tpe.typeSymbol.isTypeParam =>
        Expr.summon[ServiceTag[A]] match {
          case Some(tag) => tag
          case None =>
            (Expr.summon[Tag[A]], Expr.summon[IsNotIntersection[A]]) match {
              case (Some(tagExpr), Some(isNotIntersectionExpr)) =>
              '{ ServiceTag[A]($tagExpr, $isNotIntersectionExpr) }
              case _ =>
                report.errorAndAbort( s"Cannot find implicit ServiceTag[${tpe.show}]" )
            }
        }
      case AndType(_, _) =>
        report.errorAndAbort(s"You must not use an intersection type, yet have provided ${Type.show[A]}")
      case tpe =>
        '{
          val tag0 = Tag[A]
          new ServiceTag[A] {
            def tag: LightTypeTag =  tag0.tag
            def closestClass: Class[_] = tag0.closestClass
          }
        }
    }
  }
}