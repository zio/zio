package zio

import scala.quoted._

/**
 * Provides the `HasNoScope` instances that the compiler derives: a macro on
 * Scala 2 and an inline given on Scala 3.
 */
private[zio] abstract class HasNoScopeCompanionVersionSpecific {

  /** The value that every derived `HasNoScope` instance is cast from. */
  val instance: HasNoScope[Any]

  /**
   * Derives the evidence that `R` does not contain `zio.Scope`. `R` can be a
   * single type or an intersection such as `A with B`. It does not compile when
   * `R` is or contains `zio.Scope` ("The type X contains a zio.Scope. This is
   * not allowed."), and when `R` is or contains an abstract type parameter
   * ("Can not prove that X does not contain a zio.Scope. Please add a context
   * bound X: HasNoScope."), where `X` is the name of the type.
   */
  final inline given hasNoScope[R]: HasNoScope[R] =
    ${ HasNoScopeMacro.noScope[R] }

}

/** The macro implementation behind `HasNoScope.hasNoScope`. */
private[zio] object HasNoScopeMacro {

  def noScope[R: Type](using Quotes): Expr[HasNoScope[R]] = {
    import quotes.reflect._

    def intersectionTypes(tpe: TypeRepr): List[Symbol] = tpe.dealias match {
      case AndType(left, right) => intersectionTypes(left) ++ intersectionTypes(right)
      case other                => List(other.typeSymbol)
    }

    val rTypes    = intersectionTypes(TypeRepr.of[R])
    val scopeType = TypeRepr.of[zio.Scope].typeSymbol

    if (rTypes.contains(scopeType)) {
      val rName = TypeRepr.of[R].typeSymbol.name
      report.errorAndAbort(s"The type $rName contains a zio.Scope. This is not allowed.")
    } else if (rTypes.exists(_.isTypeParam)) {
      val rName = rTypes.find(_.isTypeParam).get.name
      report.errorAndAbort(
        s"Can not prove that $rName does not contain a zio.Scope. Please add a context bound ${rName}: HasNoScope."
      )
    } else {
      '{ HasNoScope.instance.asInstanceOf[HasNoScope[R]] }
    }
  }
}
