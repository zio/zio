package zio

import scala.quoted._

object Macros {
  def summonTag[A: Type](using ctx: Quotes): Expr[Tag[A]] =
    new Macros(ctx).summonTag[A]
}

class Macros(val ctx: Quotes) {
  given Quotes = ctx
  import ctx.reflect.*

  implicit val toExpr:  ToExpr[LightTypeTag] = new ToExpr[LightTypeTag] {
    def apply(tag: LightTypeTag)(using Quotes) = {
      import LightTypeTag.*
      tag match {
        case Primitive(name)          => '{ Primitive(${ Expr(name) }) }
        case Union(left, right)       => '{ Union(${ Expr(left) }, ${ Expr(right) }) }
        case Intersection(left, right) => '{ Intersection(${ Expr(left) }, ${ Expr(right) }) }
        case Apply(tag, args)         => '{ Apply(${ Expr(tag) }, ${ Expr(args) }) }
        case Bounds(lower, upper)     => '{ Bounds(${ Expr(lower) }, ${ Expr(upper) }) }
        case TypeRef(parent, name)    => '{ TypeRef(${ Expr(parent) }, ${ Expr(name) }) }
        case TermRef(parent, name)    => '{ TermRef(${ Expr(parent) }, ${ Expr(name) }) }
        case NoPrefix                 => '{ NoPrefix }
        case Recursive(tpe)           => '{ Recursive(${ Expr(tpe) }) }
        case NothingType => '{ NothingType }
        case AnyType => '{ AnyType }
      }
      }
    }

  def summonTag[A: Type]: Expr[Tag[A]] = {
//     println(TypeRepr.of[A])
//     println(TypeRepr.of[A].show)
    val tag0 = makeTag(TypeRepr.of[A].widen)
//     println(tag0)
//     println(tag0.render)
    '{
      Tag(${ Expr(tag0) })
    }
 }

 val nothingTypeRepr = TypeRepr.of[Nothing]
 val anyTypeRepr = TypeRepr.of[Any]

  def makeTag(typeRepr0: TypeRepr)(using seen: Set[TypeRepr] = Set.empty): LightTypeTag = {
    val typeRepr = typeRepr0.widen.dealias
    given Set[TypeRepr] = seen + typeRepr

    if (seen.contains(typeRepr)) {
      return LightTypeTag.Recursive(typeRepr.show)
    }

    typeRepr match {
      case `nothingTypeRepr` => LightTypeTag.NothingType
      case `anyTypeRepr` => LightTypeTag.AnyType
      case AppliedType(lhs, args) => LightTypeTag.Apply(makeTag(lhs), args.map(makeTag))
      case ThisType(nested)       => makeTag(nested)
      case NoPrefix()             => LightTypeTag.NoPrefix
      case TermRef(lhs, name)     => LightTypeTag.TermRef(makeTag(lhs), name)
      case TypeRef(lhs, name)     => LightTypeTag.TypeRef(makeTag(lhs), name)
      case TypeBounds(lo, hi)     => LightTypeTag.Bounds(makeTag(lo), makeTag(hi))
      case AndType(left, right)   => LightTypeTag.Intersection(makeTag(left), makeTag(right))
      case OrType(left, right)    => LightTypeTag.Union(makeTag(left), makeTag(right))
      case other                  => throw new Error(other.toString)
    }
   }


}

