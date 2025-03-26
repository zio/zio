package zio.tagging.internal

import scala.reflect.macros.whitebox

class MinusMacros(val c: whitebox.Context) {
  import c.universe._

  private val badTypes = Set(c.weakTypeOf[AnyRef], c.weakTypeOf[Any], c.weakTypeOf[Object])

  private def flattenContains(tpe: Type, M: Type): Boolean = tpe.widen.dealias match {
    case RefinedType(parents, _) => parents.exists(_ =:= M) || parents.exists(flattenContains(_, M))
    case tpe if tpe =:= M        => true
    case _                       => false
  }

  private def flattenFilter(tpe: Type, M: Type): List[Type] = tpe.widen.dealias match {
    case RefinedType(parents, _) =>
      parents.filterNot(_ =:= M).flatMap(flattenFilter(_, M))
    case _ => List(tpe)
  }

  def materialize[R: WeakTypeTag, M: WeakTypeTag, O]: Tree = {
    val R = weakTypeOf[R]
    val M = weakTypeOf[M]
    R.widen.dealias match {
      case refinedType: RefinedType =>
        val remainder = flattenFilter(refinedType, M).filterNot(badTypes).distinct
        if (flattenContains(refinedType, M)) {
          if (remainder.isEmpty) {
            q"_root_.zio.tagging.internal.Minus[$R, $M, ${weakTypeOf[Any]}]"
          } else if (remainder.length == 1) {
            q"_root_.zio.tagging.internal.Minus[$R, $M, ${remainder.head}]"
          } else {
            val O = internal.refinedType(remainder, refinedType.decls)
            q"_root_.zio.tagging.internal.Minus[$R, $M, $O]"
          }
        } else {
          c.info(
            c.enclosingPosition,
            s"does not contain ${M.widen.dealias}: ${refinedType.parents.map(_.widen.dealias)}\n$remainder",
            force = true
          )
          c.abort(c.enclosingPosition, s"Cannot subtract: $R do not contains $M")
        }
      case r if r =:= M =>
        q"_root_.zio.tagging.internal.Minus[$R, $M, Any]"
      case _ =>
        c.abort(c.enclosingPosition, s"Cannot subtract: unknown type of $R")
    }
  }
}
