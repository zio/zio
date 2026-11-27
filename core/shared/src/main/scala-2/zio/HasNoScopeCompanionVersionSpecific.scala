package zio

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

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
  implicit def noScope[R]: HasNoScope[R] =
    macro HasNoScopeMacro.impl[R]
}

/** The macro implementation behind `HasNoScope.noScope`. */
private[zio] class HasNoScopeMacro(val c: blackbox.Context) {
  import c.universe._

  /**
   * Returns the types that make up an intersection: for `A with B with C` it
   * returns `List(A, B, C)`. For a type that is not an intersection it returns
   * a list with that type.
   */
  def intersectionTypes(self: Type): List[Type] =
    self.dealias match {
      case t: RefinedType =>
        t.parents.flatMap(intersectionTypes)
      case TypeRef(_, sym, _) if sym.info.isInstanceOf[RefinedTypeApi] =>
        intersectionTypes(sym.info)
      case other =>
        List(other)
    }

  def impl[R: c.WeakTypeTag]: c.Expr[HasNoScope[R]] = {
    import c._

    val rType     = weakTypeOf[R]
    val rTypes    = intersectionTypes(rType.dealias.map(_.dealias))
    val scopeType = weakTypeOf[zio.Scope]
    if (rTypes.contains(scopeType)) {
      val rName = rType.dealias.typeSymbol.name
      c.abort(
        c.enclosingPosition,
        s"The type $rName contains a zio.Scope. This is not allowed."
      )
    } else if (rType.typeSymbol.isParameter) {
      val rName = rType.dealias.typeSymbol.name
      c.abort(
        c.enclosingPosition,
        s"Can not prove that $rName does not contain a zio.Scope. Please add a context bound $rName: HasNoScope."
      )
    } else if (rTypes.exists(_.typeSymbol.isParameter)) {
      val rName = rTypes.find(_.typeSymbol.isParameter).get.typeSymbol.name
      c.abort(
        c.enclosingPosition,
        s"Can not prove that $rName does not contain a zio.Scope. Please add a context bound $rName: HasNoScope."
      )
    } else {
      reify(HasNoScope.instance.asInstanceOf[HasNoScope[R]])
    }
  }

}
