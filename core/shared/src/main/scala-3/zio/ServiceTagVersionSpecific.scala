package zio

import scala.quoted.*

/**
 * Provides the implicit `Tag` materializer of the `Tag` object. Scala 3 adds a
 * `derived` method.
 */
private[zio] transparent trait TagVersionSpecific {

  /** Materializes the same `Tag[A]` as `materialize`, for explicit use. */
  inline def derived[A]: Tag[A] =
    ${ TagMacros.materialize[A] }

  /**
   * Materializes the `Tag[A]` at compile time. When `A` is an abstract type
   * parameter, it uses the `Tag` found in implicit scope, and it does not
   * compile when there is none. Tags for intersection types are rejected: on
   * Scala 2 for `A with B` itself, and on Scala 3 for an intersection in a
   * covariant position (as in `List[A & B]`) and a union in a contravariant
   * position inside its type arguments.
   */
  implicit inline def materialize[A]: Tag[A] =
    ${ TagMacros.materialize[A] }
}

private object TagMacros {

  def materialize[A: Type](using Quotes): Expr[Tag[A]] = {
    import quotes.reflect.*

    def isCovariant(typeParam: Symbol)     = typeParam.flags.is(Flags.Covariant)
    def isContravariant(typeParam: Symbol) = typeParam.flags.is(Flags.Contravariant)

    def findIntersectionInCovariantPosition(tpe: TypeRepr, covariant: Boolean = true): Option[TypeRepr] = tpe match {
      case AndType(_, _) => Option.when(covariant)(tpe)
      case OrType(_, _)  => Option.unless(covariant)(tpe)
      case AppliedType(constructor, typeArgs) =>
        val typeParams = constructor.typeSymbol.typeMembers.iterator.filter(_.isTypeParam)
        typeParams
          .zip(typeArgs)
          .collectFirst(Function.unlift { (typeParam, typeArg) =>
            if isContravariant(typeParam) then findIntersectionInCovariantPosition(typeArg, !covariant)
            else if isCovariant(typeParam) then findIntersectionInCovariantPosition(typeArg, covariant)
            else None
          })
      case _ => None
    }

    // Note: this error message is currently suppressed by the @implicitNotFound annotation on the parent of Tag.
    def intersectionFound(tpe: TypeRepr) =
      report.errorAndAbort(s"You must not use an intersection type, yet have provided ${tpe.show}")

    val tpe = TypeRepr.of[A].dealias
    if tpe.typeSymbol.isTypeParam || tpe.typeSymbol.isAbstractType then '{ Tag[A] }
    else findIntersectionInCovariantPosition(tpe).fold('{ Tag[A] })(intersectionFound)
  }
}
