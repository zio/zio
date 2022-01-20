package zio.internal.macros

import scala.reflect.macros.blackbox

class InternalMacros(val c: blackbox.Context) {
  import c.universe._

  def materializeServiceTag[A: c.WeakTypeTag]: c.Tree = {
    val tpe = c.weakTypeOf[A]
    tpe.widen.dealias match {
      case x if x.typeSymbol.isParameter =>
        try {
          val serviceTagType = c.typecheck(q"_root_.zio.ServiceTag[$x]").tpe
          c.inferImplicitValue(serviceTagType, silent = false, true)
        } catch {
          case _: Throwable =>
            try {
//              println(s"CREATING A SERVICE TAG ${x}")

              val intersectionType = c.typecheck(q"_root_.zio.IsNotIntersection[$x]").tpe
              c.inferImplicitValue(intersectionType, silent = false)

              val tagType = c.typecheck(q"_root_.zio.Tag[$x]").tpe
              val tagTree = c.inferImplicitValue(tagType, silent = false)

              q"""
                 new _root_.zio.ServiceTag[$tpe] {
                   def tag = $tagTree.tag
                   
                   def closestClass = null
                 }
                 """
            } catch {
              case _: Throwable =>
                c.abort(c.enclosingPosition, s"Cannot find implicit for ServiceTag[$x]")
            }
        }
      case tpe if isIntersection(tpe) =>
        c.abort(c.enclosingPosition, s"A ServiceTag may not contain an intersection type, yet have provided: $tpe")
      case _ =>
        q"_root_.zio.ServiceTag[$tpe]"
    }
  }

  def materializeIsNotIntersection[A: c.WeakTypeTag]: c.Tree = {
    val tpe = c.weakTypeOf[A]
    tpe.widen.dealias match {
      case x if x.typeSymbol.isParameter =>
        try {
          val isNotIntersectionType = tq"_root_.zio.internal.IsNotIntersection[$tpe]"
          c.inferImplicitValue(isNotIntersectionType.tpe, silent = false)
        } catch {
          case _: Throwable =>
            c.abort(c.enclosingPosition, s"Cannot find implicit for IsNotIntersection[$x]")
        }
      case tpe if isIntersection(tpe) =>
        c.abort(c.enclosingPosition, s"You must not use an intersection type, yet have provided: $tpe")
      case _ =>
        q"new _root_.zio.IsNotIntersection[$tpe] {}"
    }
  }

  def flattenIntersection(tpe: Type): List[Type] = tpe match {
    case RefinedType(parents, _) => parents.flatMap(flattenIntersection)
    case t                       => List(t)
  }

  private val badTypes = Set(c.weakTypeOf[AnyRef], c.weakTypeOf[Any], c.weakTypeOf[Object])
  def isIntersection(tpe: Type): Boolean =
    tpe.widen.dealias match {
      case tpe: RefinedType if flattenIntersection(tpe).filterNot(badTypes).distinct.length > 1 => true
      case _                                                                                    => false
    }
}
