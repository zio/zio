package zio.internal.macros

// trait IsNotIntersectionVersionSpecific {
//   implicit def materialize[A]: IsNotIntersection[A] =
//     macro zio.internal.macros.InternalMacros.materializeIsNotIntersection[A]
// }

import scala.reflect.macros.blackbox

class InternalMacros(val c: blackbox.Context) {
  import c.universe._

  def materializeIsNotIntersection[A: c.WeakTypeTag]: c.Tree = {
    val tpe = c.weakTypeOf[A]
    val badTypes = Set(c.weakTypeOf[AnyRef], c.weakTypeOf[Any])
    tpe.widen.dealias match {
      case tpe : RefinedType if flattenRefinedType(tpe).filterNot(badTypes).distinct.length > 1 =>
        c.abort(c.enclosingPosition, s"You must not use an intersection type, yet have provided: $tpe")
      case _ =>
        q"new _root_.zio.IsNotIntersection[$tpe] {}"
    }
  }

  def flattenRefinedType(tpe: Type): List[Type] = tpe match {
    case RefinedType(parents, _) => parents.flatMap(flattenRefinedType)
    case t => List(t)
  }

}