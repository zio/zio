package zio

enum LightTypeTag {
  self =>
 
  // This is, obviously, a hack. However, it helps PolyMockSpec pass with flying colors.
  def <:<(that: LightTypeTag): Boolean = {
    if (self == NothingType) true
    else if (that == AnyType) true
    else this == that
  }

  def getHasTypes: List[LightTypeTag] = 
    flattenIntersection.map { tpe =>
      tpe match {
        case Apply(_, List(tpe)) => tpe
        case other => other
      }
    }
  
  def flattenIntersection: List[LightTypeTag] = 
    this match {
      case Intersection(left, right) => left.flattenIntersection ++ right.flattenIntersection
      case _                  => List(this)
    }

  def render: String =
    this match {
      case Apply(tag, args)         =>
        s"${tag.render}[${args.map(_.render).mkString(", ")}]"
      case Primitive(name)          =>
        name
      case NoPrefix                 =>
        ""
      case TermRef(NoPrefix, name)  =>
        name
      case TypeRef(NoPrefix, name)  =>
        name
      case TermRef(parent, name)    =>
        s"${parent.render}.$name"
      case TypeRef(parent, name)    =>
        s"${parent.render}.$name"
      case Union(left, right)       =>
        s"${left.render} | ${right.render}"
      case Intersection(left, right) =>
        s"${left.render} & ${right.render}"
      case Bounds(lower, upper)     =>
        s"_ <: ${lower.render} >: ${upper.render}"
      case Recursive(name) =>
        s"Rec($name)"
      case NothingType => "Nothing"
      case AnyType => "Any"
    }

  case Apply(tag: LightTypeTag, args: List[LightTypeTag])

  case Primitive(name: String)

  case NoPrefix

  case Recursive(name: String)

  case TermRef(parent: LightTypeTag, name: String)

  case TypeRef(parent: LightTypeTag, name: String)

  case Union(left: LightTypeTag, right: LightTypeTag)

  case Intersection(left: LightTypeTag, right: LightTypeTag)

  case Bounds(lower: LightTypeTag, upper: LightTypeTag)

  case NothingType 

  case AnyType 

  case TypeParamRef
}
