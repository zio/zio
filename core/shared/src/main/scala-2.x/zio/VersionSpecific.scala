package zio

import izreflect.fundamentals.reflection.Tags._
import izreflect.fundamentals.reflection.macrortti.{ LightTypeTag, LightTypeTagRef }

private[zio] trait VersionSpecific {

  type TaggedType[A]             = Tag[A]
  type TaggedTypeF[F[_]]         = TagK[F]
  type TaggedTypeF2[F[_, _]]     = TagKK[F]
  type TagggedTypeF3[F[_, _, _]] = TagK3[F]
  type TagType                   = LightTypeTag

  private[zio] val TaggedType = Tag

  private[zio] def taggedTagType[A](t: Tagged[A]): TagType = t.tag.tag

  private[zio] def taggedIsSubtype(left: TagType, right: TagType): Boolean =
    left <:< right

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
