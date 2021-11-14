package zio

import scala.reflect.macros.blackbox

case class Tag[A](tag: LightTypeTag) {

  def render: String = s"Tag(${tag.render})"
}

object Tag {
  def apply[A]: Tag[A] = macro TagMacros.tagImpl[A]

  implicit def materialize[A]: Tag[A] = macro TagMacros.tagImpl[A]
}

private[zio] class TagMacros(val c: blackbox.Context) {
  import c.universe._

  def tagImpl[A: c.WeakTypeTag]: c.Tree = {
    val tag = makeTag(c.weakTypeOf[A])(Set.empty)
    q"_root_.zio.Tag[${c.weakTypeOf[A]}]($tag)"
  }

  object TypeVariable {
    def unapply(tpe: Type): Option[Type] = tpe match {
      case x @ TypeRef(_, _, _) if x.typeSymbol.isParameter => Some(x)
      case _                                                => None
    }
  }

  val nothingType = c.weakTypeOf[Nothing]
  val anyType     = c.weakTypeOf[Any]

  def makeTag(tpe0: Type)(seen0: Set[Type]): c.Tree = {
    // reimplementing the logic in `LightTypeTag.makeTag`
    val tpe =
      tpe0.dealias.widen

    if (seen0.contains(tpe)) {
      return q"_root_.zio.LightTypeTag.Recursive(${show(tpe)})"
    }

    val seen: Set[c.universe.Type] = seen0 + tpe

    tpe match {
      case `nothingType` => q"_root_.zio.LightTypeTag.NothingType"
      case `anyType`     => q"_root_.zio.LightTypeTag.AnyType"
      case ThisType(_) =>
        throw new Error(s"THIS TYPE ${tpe} ${show(tpe)}")
//        q"_root_.zio.LightTypeTag.NoPrefix"
      case NoPrefix =>
        q"_root_.zio.LightTypeTag.NoPrefix"
      case TypeVariable(x) =>
        val tagType = appliedType(c.weakTypeOf[Tag[_]], List(x))
        val tag     = c.inferImplicitValue(tagType, false, true)
        q"$tag.tag"
      case TypeRef(lhs, name, args) =>
        q"_root_.zio.LightTypeTag.TypeRef(${makeTag(lhs)(seen)}, ${name.name.toString}, ${args.map(makeTag(_)(seen))})"
      case TypeBounds(lo, hi) =>
        q"_root_.zio.LightTypeTag.Bounds(${makeTag(lo)(seen)}, ${makeTag(hi)(seen)})"
      case RefinedType(tpes0, _) =>
        val tpes = tpes0.map(_.dealias.widen.finalResultType).filterNot(c.weakTypeOf[Object] <:< _)
        if (tpes.length == 1) makeTag(tpes.head)(seen)
        else q"_root_.zio.LightTypeTag.And(${tpes.map(makeTag(_)(seen))})"
      case PolyType(params, TypeRef(lhs, s, args)) =>
        val paramSet     = params.map(_.name.toString).toSet
        val filteredArgs = args.filterNot(arg => paramSet(arg.typeSymbol.name.toString))
        q"_root_.zio.LightTypeTag.TypeRef(${makeTag(lhs)(seen)}, ${s.name.toString}, ${filteredArgs.map(makeTag(_)(seen))})"
      case PolyType(_, rhs) =>
        q"_root_.zio.LightTypeTag.Poly(${makeTag(rhs)(seen)})"
      case ExistentialType(_, tpe) =>
        makeTag(tpe)(seen)
      case other =>
        println(s"UNKNOWN TYPE ${show(other)} ${showRaw(other)}")
        throw new Error("IMPOSSIBLE!")
    }
  }
  // Create a method that debugs every property of a symbol
  def debugSymbol(symbol: Symbol): String =
    s"""
owner: ${symbol.owner}
       """

}
