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
    val tag0 = makeTag(TypeRepr.of[A].widen)
    '{
      Tag($tag0)
    }
 }

 val nothingTypeRepr = TypeRepr.of[Nothing]
 val anyTypeRepr = TypeRepr.of[Any]

  def makeTag(typeRepr0: TypeRepr)(using seen: Set[TypeRepr] = Set.empty): Expr[LightTypeTag] = {
    val typeRepr = typeRepr0.widen.dealias
    given Set[TypeRepr] = seen + typeRepr

    if (seen.contains(typeRepr)) {
      return '{ LightTypeTag.Recursive(${Expr(typeRepr.show)}) }
    }

    typeRepr match {
      case `nothingTypeRepr` => '{ LightTypeTag.NothingType }
      case `anyTypeRepr` => '{ LightTypeTag.AnyType }
      case AppliedType(lhs, args) => '{ LightTypeTag.Apply(${makeTag(lhs)}, ${Expr.ofList(args.map(makeTag))}) }
      case ThisType(nested)       => makeTag(nested)
      case NoPrefix()             => '{ LightTypeTag.NoPrefix }
      case TermRef(lhs, name)     => '{ LightTypeTag.TermRef(${makeTag(lhs)}, ${Expr(name)}) }
      case x @ TypeRef(_, _) if x.typeSymbol.isAbstractType =>
        x.asType match { 
          case '[a] =>
            val tag = Expr.summon[Tag[a]].getOrElse(report.errorAndAbort(s"Implicit not found for Tag[${x.show}]"))
            '{ $tag.tag }
        }
      case x @TypeRef(lhs, name)     => '{ LightTypeTag.TypeRef(${makeTag(lhs)}, ${Expr(name)}) }
      case TypeBounds(lo, hi)     => '{ LightTypeTag.Bounds(${makeTag(lo)}, ${makeTag(hi)}) }
      case AndType(left, right)   => '{ LightTypeTag.Intersection(${makeTag(left)}, ${makeTag(right)}) }
      case OrType(left, right)    => '{ LightTypeTag.Union(${makeTag(left)}, ${makeTag(right)}) }
      case other                  => throw new Error(other.toString)
    }
   }


}

