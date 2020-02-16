package zio

import izumi.fundamentals.reflection.Tags.Tag
import izumi.fundamentals.reflection.macrortti.{ LightTypeTag, LightTypeTagRef }

private[zio] object ScalaSpecific {

  type TaggedType[A] = Tag[A]
  type TagType       = LightTypeTag

  private[zio] def taggedTagType[A](t: Tagged[A]): TagType = t.tag.tag

  private[zio] def taggedIsSubtype(left: TagType, right: TagType): Boolean =
    left <:< right

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] =
    t.decompose.map { parent =>
      parent.ref match {
        case reference: LightTypeTagRef.AppliedNamedReference if reference.typeArgs.size == 1 =>
          parent.typeArgs.head

        case _ =>
          parent
      }
    }
}
