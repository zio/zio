package zio

import scala.reflect.macros.blackbox
import zio.{LightTypeTag => T}

case class Tag[A](tag: LightTypeTag) {

  def render: String = tag.render
}

object Tag {
  def apply[A]: Tag[A] = macro TagMacros.tagImpl[A]

  implicit def materialize[A]: Tag[A] = macro TagMacros.tagImpl[A]
}

private[zio] class TagMacros(val c: blackbox.Context) {
  import c.universe._

  implicit private val lightTypeTagLiftable = new Liftable[LightTypeTag] {
    override def apply(value: LightTypeTag): c.universe.Tree = {
      import c.universe._
      value match {
        case T.Primitive(name)             => q"Primitive($name)"
        case T.TypeRef(parent, name, args) => q"TypeRef(${apply(parent)}, $name, ${args.map(apply)})"
        case T.NoPrefix                    => q"NoPrefix"
        case T.Recursive(tpe)              => q"Recursive($tpe)"
        case T.NothingType                 => q"NothingType"
        case T.AnyType                     => q"AnyType"
      }
    }
  }

  def tagImpl[A: c.WeakTypeTag]: c.Tree = {
    val tag = makeTag(c.weakTypeOf[A])(Set.empty)
    println(show(tag))
//    throw new Error("OH")
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
    val tpe                        = tpe0.widen.dealias
    val seen: Set[c.universe.Type] = seen0 + tpe

    if (seen0.contains(tpe)) {
      return q"_root_.zio.LightTypeTag.Recursive(${show(tpe)})"
    }

    tpe match {
      case `nothingType` => q"_root_.zio.LightTypeTag.NothingType"
      case `anyType`     => q"_root_.zio.LightTypeTag.AnyType"
//      case AppliedType(lhs, args) =>
//        q"zio.Tag.Apply(${makeTag(lhs)}, ${Expr.ofList(args.map(makeTag))})"
      case ThisType(nested) =>
//        makeTag(nested)(seen)
//        ???
        q"_root_.zio.LightTypeTag.NoPrefix"
      case NoPrefix =>
        q"_root_.zio.LightTypeTag.NoPrefix"
//      case TermRef(lhs, name) =>
//        q"_root_t_.zio.Tag.TermRef(${makeTag(lhs)}, ${Expr(name)})"
      case TypeVariable(x) =>
        println(s"TYPE VAR ${x}")
        q"implicitly[_root_.zio.Tag[$x]].tag"
//        x.asType match {
//          case '[a] =>
//            val tag = Expr.summon[Tag[a]].getOrElse(report.errorAndAbort(s"Implicit not found for Tag[${x.show}]"))
//            q"$tag.tag"
//        }
      case TypeRef(lhs, name, args) =>
        q"_root_.zio.LightTypeTag.TypeRef(${makeTag(lhs)(seen)}, ${name.name.toString}, ${args.map(makeTag(_)(seen))})"
      case TypeBounds(lo, hi) =>
        q"_root_.zio.LightTypeTag.Bounds(${makeTag(lo)(seen)}, ${makeTag(hi)(seen)})"
      case RefinedType(tpes, _) =>
        q"_root_.zio.LightTypeTag.And(${tpes.map(makeTag(_)(seen))})"

    }
  }

}
