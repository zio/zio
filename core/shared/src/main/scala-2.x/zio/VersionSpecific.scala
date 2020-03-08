package zio

import izreflect.fundamentals.reflection.Tags.Tag
import izreflect.fundamentals.reflection.macrortti.{ LightTypeTag, LightTypeTagRef }

private[zio] trait VersionSpecific {

  type Tagged[A] = Tag[A]
  type TagType   = LightTypeTag

  private[zio] def taggedIsSubtype(left: TagType, right: TagType): Boolean =
    left <:< right

  private[zio] def taggedTagType[A](tagged: Tagged[A]): TagType =
    tagged.tag

  /**
   * This method takes a tag for an intersection of [[zio.Has]]
   * and returns a set of tags for parameters of each individual `Has`:
   *
   * `Tag[Has[A] with Has[B]]` should produce `Set(Tag[A], Tag[B])`
   */
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
