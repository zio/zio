package zio

import scala.quoted.*

case class Tag[A](tag: LightTypeTag) {
  override def toString = tag.toString
  def render: String    = tag.render
}

object Tag {
  inline def apply[A] = ${ Macros.summonTag[A] }

  implicit inline def make[A]: Tag[A] = ${ Macros.summonTag[A] }
}




