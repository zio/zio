package zio

import zio.IsNotIntersection

final case class Tag[A](
  typeTag: izumi.reflect.Tag[A],
  isNotIntersection: IsNotIntersection[A]
)

object Tag extends TagLowPriority {

  def apply[A](implicit tag: Tag[A]): Tag[A] = tag

  implicit def nothingTag: Tag[Nothing] =
    Tag[Nothing](implicitly[izumi.reflect.Tag[Nothing]], implicitly[IsNotIntersection[Nothing]])
}

trait TagLowPriority extends TagLowPriority2 {

  implicit def anyTag: Tag[Any] =
    Tag(implicitly[izumi.reflect.Tag[Any]], implicitly[IsNotIntersection[Any]])
}

trait TagLowPriority2 extends TagLowPriority3 {

  implicit def derive6[F[_, _, _, _, _, _], A, B, C, D, E, G](implicit
    tag: Tag[A],
    tag2: Tag[B],
    tag3: Tag[C],
    tag4: Tag[D],
    tag5: Tag[E],
    tag6: Tag[G],
    deriveTag: DeriveTag6[F]
  ): Tag[F[A, B, C, D, E, G]] = {
    implicit val typeTag: izumi.reflect.Tag[A] = tag.typeTag
    Tag(???, ???)
  }

}

trait TagLowPriority3 extends TagLowPriority4 {

  implicit def derive5[F[_, _, _, _, _], A, B, C, D, E](implicit
    tag: Tag[A],
    tag2: Tag[B],
    tag3: Tag[C],
    tag4: Tag[D],
    tag5: Tag[E],
    deriveTag: DeriveTag5[F]
  ): Tag[F[A, B, C, D, E]] = {
    implicit val typeTag: izumi.reflect.Tag[A] = tag.typeTag
    Tag(???, ???)
  }

}

trait TagLowPriority4 extends TagLowPriority5 {
  implicit def derive4[F[_, _, _, _], A, B, C, D](implicit
    tag: Tag[A],
    tag2: Tag[B],
    tag3: Tag[C],
    tag4: Tag[D],
    deriveTag: DeriveTag4[F]
  ): Tag[F[A, B, C, D]] = {
    implicit val typeTag: izumi.reflect.Tag[A] = tag.typeTag
    Tag(???, ???)
  }
}

trait TagLowPriority5 extends TagLowPriority6 {
  implicit def derive3[F[_, _, _], A, B, C](implicit
    tag: Tag[A],
    tag2: Tag[B],
    tag3: Tag[C],
    deriveTag: DeriveTag3[F]
  ): Tag[F[A, B, C]] = {
    implicit val typeTag: izumi.reflect.Tag[A] = tag.typeTag
    Tag(???, ???)
  }
}

trait TagLowPriority6 extends TagLowPriority7 {
  implicit def derive2[F[_, _], A, B](implicit tag: Tag[A], tag2: Tag[B], deriveTag: DeriveTag2[F]): Tag[F[A, B]] = {
    implicit val tagK2: izumi.reflect.TagKK[F]  = deriveTag.typeTag
    implicit val typeTag1: izumi.reflect.Tag[A] = tag.typeTag
    implicit val typeTag2: izumi.reflect.Tag[B] = tag2.typeTag
    Tag(
      izumi.reflect.Tag.appliedTag(tagK2, List(typeTag1.tag, typeTag2.tag)),
      deriveTag.isNotIntersection.asInstanceOf[IsNotIntersection[F[A, B]]]
    )
  }
}

trait TagLowPriority7 extends TagLowPriority8 {
  implicit def derive1[F[_], A](implicit tag: Tag[A], deriveTag: DeriveTag[F]): Tag[F[A]] = {
    implicit val tagK: izumi.reflect.TagK[F]   = deriveTag.typeTag
    implicit val typeTag: izumi.reflect.Tag[A] = tag.typeTag
    Tag(
      izumi.reflect.Tag.appliedTag(tagK, List(typeTag.tag)),
      deriveTag.isNotIntersection.asInstanceOf[IsNotIntersection[F[A]]]
    )
  }
}

trait TagLowPriority8 {
  implicit def derive[A](implicit tag: izumi.reflect.Tag[A], isNotIntersection: IsNotIntersection[A]): Tag[A] =
    Tag(tag, isNotIntersection)
}

final case class DeriveTag[F[_]](typeTag: izumi.reflect.TagK[F], isNotIntersection: IsNotIntersection[F[Any]])

object DeriveTag {

  implicit def derive[F[_]](implicit
    typeTag: izumi.reflect.TagK[F],
    isNotIntersection: IsNotIntersection[F[Any]]
  ): DeriveTag[F] =
    DeriveTag[F](typeTag, isNotIntersection)
}

final case class DeriveTag2[F[_, _]](typeTag: izumi.reflect.TagKK[F], isNotIntersection: IsNotIntersection[F[Any, Any]])

object DeriveTag2 {

  implicit def derive[F[_, _]](implicit
    typeTag: izumi.reflect.TagKK[F],
    isNotIntersection: IsNotIntersection[F[Any, Any]]
  ): DeriveTag2[F] =
    DeriveTag2[F](typeTag, isNotIntersection)
}

final case class DeriveTag3[F[_, _, _]](
  typeTag: izumi.reflect.TagK3[F],
  isNotIntersection: IsNotIntersection[F[Any, Any, Any]]
)

object DeriveTag3 {

  implicit def derive[F[_, _, _]](implicit
    typeTag: izumi.reflect.TagK3[F],
    isNotIntersection: IsNotIntersection[F[Any, Any, Any]]
  ): DeriveTag3[F] =
    DeriveTag3[F](typeTag, isNotIntersection)
}

final case class DeriveTag4[F[_, _, _, _]](
  typeTag: izumi.reflect.TagK4[F],
  isNotIntersection: IsNotIntersection[F[Any, Any, Any, Any]]
)

object DeriveTag4 {

  implicit def derive[F[_, _, _, _]](implicit
    typeTag: izumi.reflect.TagK4[F],
    isNotIntersection: IsNotIntersection[F[Any, Any, Any, Any]]
  ): DeriveTag4[F] =
    DeriveTag4[F](typeTag, isNotIntersection)
}

final case class DeriveTag5[F[_, _, _, _, _]](
  typeTag: izumi.reflect.TagK5[F],
  isNotIntersection: IsNotIntersection[F[Any, Any, Any, Any, Any]]
)

object DeriveTag5 {

  implicit def derive[F[_, _, _, _, _]](implicit
    typeTag: izumi.reflect.TagK5[F],
    isNotIntersection: IsNotIntersection[F[Any, Any, Any, Any, Any]]
  ): DeriveTag5[F] =
    DeriveTag5[F](typeTag, isNotIntersection)
}

final case class DeriveTag6[F[_, _, _, _, _, _]](
  typeTag: izumi.reflect.TagK6[F],
  isNotIntersection: IsNotIntersection[F[Any, Any, Any, Any, Any, Any]]
)

object DeriveTag6 {

  implicit def derive[F[_, _, _, _, _, _]](implicit
    typeTag: izumi.reflect.TagK6[F],
    isNotIntersection: IsNotIntersection[F[Any, Any, Any, Any, Any, Any]]
  ): DeriveTag6[F] =
    DeriveTag6[F](typeTag, isNotIntersection)
}
