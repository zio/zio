package zio

import scala.quoted.*

trait IsNotIntersectionVersionSpecific {
  implicit inline def materialize[A]: IsNotIntersection[A] =
    ${ IsNotIntersectionMacros.materialize[A] }
}

private object IsNotIntersectionMacros {
  def materialize[A: Type](using Quotes): Expr[IsNotIntersection[A]] = {
    import quotes.reflect.*
    TypeRepr.of[A].dealias match {
      case AndType(_, _) =>
        report.errorAndAbort(s"You must not use an intersection type, yet have provided ${Type.show[A]}")
      case _ =>
        '{ new IsNotIntersection[A] {} }
    }
  }
}