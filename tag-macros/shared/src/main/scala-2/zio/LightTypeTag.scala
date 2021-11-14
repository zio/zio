package zio

import zio.LightTypeTag.{And, AnyType, NoPrefix, NothingType, Primitive, Recursive, TypeRef}

sealed trait LightTypeTag { self =>
  def getHasTypes: List[LightTypeTag] =
    flattenIntersection.map {
      case TypeRef(_, _, List(tpe)) => tpe
      case other                    => other
    }

  private def flattenIntersection: List[LightTypeTag] =
    this match {
      case And(tpes) => tpes.flatMap(_.flattenIntersection)
      case _         => List(this)
    }

  def <:<(that: LightTypeTag): Boolean =
    if (self == NothingType) true
    else if (that == AnyType) true
    else this == that

  def render: String =
    self match {
//      case Apply(tag, args) =>
//        s"${tag.render}[${args.map(_.render).mkString(", ")}]"
      case Primitive(name) =>
        name
      case NoPrefix =>
        ""
      case TypeRef(NoPrefix, name, Nil) =>
        name
      case TypeRef(parent, name, Nil) =>
        s"${parent.render}.$name"
      case TypeRef(NoPrefix, name, args) =>
        s"$name[${args.map(_.render)}]"
      case TypeRef(parent, name, args) =>
        s"${parent.render}.$name[${args.map(_.render)}]"
      case Recursive(name) =>
        s"Rec($name)"
      case NothingType => "Nothing"
      case AnyType     => "Any"
      case And(tpes)   => tpes.map(_.render).mkString(" & ")
    }

}

object LightTypeTag { self =>

  case class Primitive(name: String) extends LightTypeTag

  case object NoPrefix extends LightTypeTag

  case class Recursive(name: String) extends LightTypeTag

  case class TypeRef(parent: LightTypeTag, name: String, args: List[LightTypeTag]) extends LightTypeTag

  case class And(tpes: List[LightTypeTag]) extends LightTypeTag

  case object NothingType extends LightTypeTag

  case object AnyType extends LightTypeTag

}
