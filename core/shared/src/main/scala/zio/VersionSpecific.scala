package zio

import izumi.reflect.macrortti.{ LightTypeTag, LightTypeTagRef }

private[zio] trait VersionSpecific {

  type Tagged[A] = izumi.reflect.Tag[A]
  type TagType   = LightTypeTag

  type TaggedF[F[_]]                                                                  = izumi.reflect.TagK[F]
  type TaggedF2[F[_, _]]                                                              = izumi.reflect.TagKK[F]
  type TaggedF3[F[_, _, _]]                                                           = izumi.reflect.TagK3[F]
  type TaggedF4[F[_, _, _, _]]                                                        = izumi.reflect.TagK4[F]
  type TaggedF5[F[_, _, _, _, _]]                                                     = izumi.reflect.TagK5[F]
  type TaggedF6[F[_, _, _, _, _, _]]                                                  = izumi.reflect.TagK6[F]
  type TaggedF7[F[_, _, _, _, _, _, _]]                                               = izumi.reflect.TagK7[F]
  type TaggedF8[F[_, _, _, _, _, _, _, _]]                                            = izumi.reflect.TagK8[F]
  type TaggedF9[F[_, _, _, _, _, _, _, _, _]]                                         = izumi.reflect.TagK9[F]
  type TaggedF10[F[_, _, _, _, _, _, _, _, _, _]]                                     = izumi.reflect.TagK10[F]
  type TaggedF11[F[_, _, _, _, _, _, _, _, _, _, _]]                                  = izumi.reflect.TagK11[F]
  type TaggedF12[F[_, _, _, _, _, _, _, _, _, _, _, _]]                               = izumi.reflect.TagK12[F]
  type TaggedF13[F[_, _, _, _, _, _, _, _, _, _, _, _, _]]                            = izumi.reflect.TagK13[F]
  type TaggedF14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _]]                         = izumi.reflect.TagK14[F]
  type TaggedF15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                      = izumi.reflect.TagK15[F]
  type TaggedF16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                   = izumi.reflect.TagK16[F]
  type TaggedF17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                = izumi.reflect.TagK17[F]
  type TaggedF18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]             = izumi.reflect.TagK18[F]
  type TaggedF19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]          = izumi.reflect.TagK19[F]
  type TaggedF20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]       = izumi.reflect.TagK20[F]
  type TaggedF21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]    = izumi.reflect.TagK21[F]
  type TaggedF22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK22[F]

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
