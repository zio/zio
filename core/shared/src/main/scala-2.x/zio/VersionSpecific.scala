package zio

import izumi.reflect.{ Tag, TagK, TagKK, TagK3 }
import izumi.reflect.macrortti.{ LightTypeTag, LightTypeTagRef }

private[zio] trait VersionSpecific {

  type Tagged[A] = Tag[A]
  type TagType   = LightTypeTag

  type TaggedF[F[_]] = TagK[F]
  type TaggedF2[F[_, _]] = TagKK[F]
  type TaggedF3[F[_, _, _]] = TagK3[F]
  type TaggedF4[F[_, _, _, _]] = Tag.auto.T[F]
  type TaggedF5[F[_, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF6[F[_, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF7[F[_, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF8[F[_, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF9[F[_, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF10[F[_, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF11[F[_, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF12[F[_, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF13[F[_, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]
  type TaggedF22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = Tag.auto.T[F]

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
