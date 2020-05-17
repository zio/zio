package zio

import izumi.reflect.macrortti.LightTypeTagRef

private[zio] trait VersionSpecific {

  type Tag[A] = izumi.reflect.Tag[A]
  lazy val Tag = izumi.reflect.Tag

  type TagK[F[_]] = izumi.reflect.TagK[F]
  lazy val TagK = izumi.reflect.TagK

  type TagKK[F[_, _]] = izumi.reflect.TagKK[F]
  lazy val TagKK = izumi.reflect.TagKK

  type TagK3[F[_, _, _]] = izumi.reflect.TagK3[F]
  lazy val TagK3 = izumi.reflect.TagK3

  type TagK4[F[_, _, _, _]]                                                        = izumi.reflect.TagK4[F]
  type TagK5[F[_, _, _, _, _]]                                                     = izumi.reflect.TagK5[F]
  type TagK6[F[_, _, _, _, _, _]]                                                  = izumi.reflect.TagK6[F]
  type TagK7[F[_, _, _, _, _, _, _]]                                               = izumi.reflect.TagK7[F]
  type TagK8[F[_, _, _, _, _, _, _, _]]                                            = izumi.reflect.TagK8[F]
  type TagK9[F[_, _, _, _, _, _, _, _, _]]                                         = izumi.reflect.TagK9[F]
  type TagK10[F[_, _, _, _, _, _, _, _, _, _]]                                     = izumi.reflect.TagK10[F]
  type TagK11[F[_, _, _, _, _, _, _, _, _, _, _]]                                  = izumi.reflect.TagK11[F]
  type TagK12[F[_, _, _, _, _, _, _, _, _, _, _, _]]                               = izumi.reflect.TagK12[F]
  type TagK13[F[_, _, _, _, _, _, _, _, _, _, _, _, _]]                            = izumi.reflect.TagK13[F]
  type TagK14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _]]                         = izumi.reflect.TagK14[F]
  type TagK15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                      = izumi.reflect.TagK15[F]
  type TagK16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                   = izumi.reflect.TagK16[F]
  type TagK17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]                = izumi.reflect.TagK17[F]
  type TagK18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]             = izumi.reflect.TagK18[F]
  type TagK19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]          = izumi.reflect.TagK19[F]
  type TagK20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]       = izumi.reflect.TagK20[F]
  type TagK21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]    = izumi.reflect.TagK21[F]
  type TagK22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK22[F]

  type LightTypeTag = izumi.reflect.macrortti.LightTypeTag

  private[zio] def taggedIsSubtype(left: LightTypeTag, right: LightTypeTag): Boolean =
    left <:< right

  private[zio] def taggedTagType[A](tagged: Tag[A]): LightTypeTag =
    tagged.tag

  /**
   * This method takes a tag for an intersection of [[zio.Has]]
   * and returns a set of tags for parameters of each individual `Has`:
   *
   * `Tag[Has[A] with Has[B]]` should produce `Set(Tag[A], Tag[B])`
   */
  private[zio] def taggedGetHasServices[A](t: LightTypeTag): Set[LightTypeTag] =
    t.decompose.map { parent =>
      parent.ref match {
        case reference: LightTypeTagRef.AppliedNamedReference if reference.typeArgs.size == 1 =>
          parent.typeArgs.head

        case _ =>
          parent
      }
    }

  @deprecated("use Tag", "1.0.0") type Tagged[A]                                              = izumi.reflect.Tag[A]
  @deprecated("use LightTypeTag", "1.0.0") type TypeTag                                       = izumi.reflect.macrortti.LightTypeTag
  @deprecated("use TagK", "1.0.0") type TaggedF[F[_]]                                         = izumi.reflect.TagK[F]
  @deprecated("use TagKK", "1.0.0") type TaggedF2[F[_, _]]                                    = izumi.reflect.TagKK[F]
  @deprecated("use TagK3", "1.0.0") type TaggedF3[F[_, _, _]]                                 = izumi.reflect.TagK3[F]
  @deprecated("use TagK4", "1.0.0") type TaggedF4[F[_, _, _, _]]                              = izumi.reflect.TagK4[F]
  @deprecated("use TagK5", "1.0.0") type TaggedF5[F[_, _, _, _, _]]                           = izumi.reflect.TagK5[F]
  @deprecated("use TagK6", "1.0.0") type TaggedF6[F[_, _, _, _, _, _]]                        = izumi.reflect.TagK6[F]
  @deprecated("use TagK7", "1.0.0") type TaggedF7[F[_, _, _, _, _, _, _]]                     = izumi.reflect.TagK7[F]
  @deprecated("use TagK8", "1.0.0") type TaggedF8[F[_, _, _, _, _, _, _, _]]                  = izumi.reflect.TagK8[F]
  @deprecated("use TagK9", "1.0.0") type TaggedF9[F[_, _, _, _, _, _, _, _, _]]               = izumi.reflect.TagK9[F]
  @deprecated("use TagK10", "1.0.0") type TaggedF10[F[_, _, _, _, _, _, _, _, _, _]]          = izumi.reflect.TagK10[F]
  @deprecated("use TagK11", "1.0.0") type TaggedF11[F[_, _, _, _, _, _, _, _, _, _, _]]       = izumi.reflect.TagK11[F]
  @deprecated("use TagK12", "1.0.0") type TaggedF12[F[_, _, _, _, _, _, _, _, _, _, _, _]]    = izumi.reflect.TagK12[F]
  @deprecated("use TagK13", "1.0.0") type TaggedF13[F[_, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK13[F]
  @deprecated("use TagK14", "1.0.0")
  type TaggedF14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK14[F]
  @deprecated("use TagK15", "1.0.0")
  type TaggedF15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK15[F]
  @deprecated("use TagK16", "1.0.0")
  type TaggedF16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK16[F]
  @deprecated("use TagK17", "1.0.0")
  type TaggedF17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK17[F]
  @deprecated("use TagK18", "1.0.0")
  type TaggedF18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK18[F]
  @deprecated("use TagK19", "1.0.0")
  type TaggedF19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK19[F]
  @deprecated("use TagK20", "1.0.0")
  type TaggedF20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK20[F]
  @deprecated("use TagK21", "1.0.0")
  type TaggedF21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK21[F]
  @deprecated("use TagK22", "1.0.0")
  type TaggedF22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = izumi.reflect.TagK22[F]
}
