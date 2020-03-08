package zio

import izreflect.fundamentals.reflection.Tags._
import izreflect.fundamentals.reflection.macrortti.{ LightTypeTag, LightTypeTagRef }

private[zio] trait VersionSpecific {

  type TaggedType[A] = Tag[A]
  type TaggedTypeF[F[_]] = TagK[F]
  type TaggedTypeF2[F[_, _]] = TagKK[F]
  type TaggedTypeF3[F[_, _, _]] = TagK3[F]
  type TaggedTypeF4[F[_, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3] = F[A0, A1, A2, A3] } ]
  type TaggedTypeF5[F[_, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4] = F[A0, A1, A2, A3, A4] } ]
  type TaggedTypeF6[F[_, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5] = F[A0, A1, A2, A3, A4, A5] } ]
  type TaggedTypeF7[F[_, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6] = F[A0, A1, A2, A3, A4, A5, A6] } ]
  type TaggedTypeF8[F[_, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7] = F[A0, A1, A2, A3, A4, A5, A6, A7] } ]
  type TaggedTypeF9[F[_, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8] } ]
  type TaggedTypeF10[F[_, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9] } ]
  type TaggedTypeF11[F[_, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10] } ]
  type TaggedTypeF12[F[_, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11] } ]
  type TaggedTypeF13[F[_, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12] } ]
  type TaggedTypeF14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13] } ]
  type TaggedTypeF15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14] } ]
  type TaggedTypeF16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15] } ]
  type TaggedTypeF17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16] } ]
  type TaggedTypeF18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17] } ]
  type TaggedTypeF19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18] } ]
  type TaggedTypeF20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19] } ]
  type TaggedTypeF21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20] } ]
  type TaggedTypeF22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]] = HKTag[ { type Arg[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21] = F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21] } ]
  type TagType = LightTypeTag

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
