package zio

import scala.reflect.macros.blackbox

case class Tag[A]() {
  val tag: LightTypeTag = ???

  def render: String = ???
}

object Tag {
  implicit def materialize[A]: Tag[A] = macro TagMacros.tagImpl[A]
}

sealed trait LightTypeTag {
  def getHasTypes: List[LightTypeTag] = ???

  def <:<(right: LightTypeTag) = true

  def render: String = ???

}

object LightTypeTag {}

private[zio] class TagMacros(val c: blackbox.Context) {
  import c.universe._

  def tagImpl[A: c.WeakTypeTag]: c.Tree =
    q"_root_.zio.Tag[${c.weakTypeOf[A]}]()"
}
