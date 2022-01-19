package zio

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

trait TagLowPriority3 extends TagLowPriority4 {

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

trait TagLowPriority4 extends TagLowPriority5 {
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

trait TagLowPriority5 extends TagLowPriority6 {
  implicit def derive2[F[_, _], A, B](implicit tag: Tag[A], tag2: Tag[B], deriveTag: DeriveTag2[F]): Tag[F[A, B]] = {
    implicit val tagK2                          = deriveTag.typeTag
    implicit val typeTag1: izumi.reflect.Tag[A] = tag.typeTag
    implicit val typeTag2: izumi.reflect.Tag[B] = tag2.typeTag
    Tag(implicitly, deriveTag.isNotIntersection.asInstanceOf[IsNotIntersection[F[A, B]]])
  }
}

trait TagLowPriority6 extends TagLowPriority7 {
  implicit def derive1[F[_], A](implicit tag: Tag[A], deriveTag: DeriveTag[F]): Tag[F[A]] = {
    implicit val tagK                          = deriveTag.typeTag
    implicit val typeTag: izumi.reflect.Tag[A] = tag.typeTag
    Tag(implicitly, deriveTag.isNotIntersection.asInstanceOf[IsNotIntersection[F[A]]])
  }
}

trait TagLowPriority7 {
  implicit def derive[A](implicit tag: izumi.reflect.Tag[A], isNotIntersection: IsNotIntersection[A]): Tag[A] =
    Tag(tag, isNotIntersection)
}

final case class DeriveTag[F[_]](typeTag: izumi.reflect.TagK[F], isNotIntersection: IsNotIntersection[F[_]])

object DeriveTag {

  implicit def derive[F[_]](implicit
    typeTag: izumi.reflect.TagK[F],
    isNotIntersection: IsNotIntersection[F[_]]
  ): DeriveTag[F] =
    DeriveTag[F](typeTag, isNotIntersection)
}

final case class DeriveTag2[F[_, _]](typeTag: izumi.reflect.TagKK[F], isNotIntersection: IsNotIntersection[F[_, _]])

object DeriveTag2 {

  implicit def derive[F[_, _]](implicit
    typeTag: izumi.reflect.TagKK[F],
    isNotIntersection: IsNotIntersection[F[_, _]]
  ): DeriveTag2[F] =
    DeriveTag2[F](typeTag, isNotIntersection)
}

final case class DeriveTag3[F[_, _, _]](
  typeTag: izumi.reflect.TagK3[F],
  isNotIntersection: IsNotIntersection[F[_, _, _]]
)

object DeriveTag3 {

  implicit def derive[F[_, _, _]](implicit
    typeTag: izumi.reflect.TagK3[F],
    isNotIntersection: IsNotIntersection[F[_, _, _]]
  ): DeriveTag3[F] =
    DeriveTag3[F](typeTag, isNotIntersection)
}

final case class DeriveTag4[F[_, _, _, _]](
  typeTag: izumi.reflect.TagK4[F],
  isNotIntersection: IsNotIntersection[F[_, _, _, _]]
)

object DeriveTag4 {

  implicit def derive[F[_, _, _, _]](implicit
    typeTag: izumi.reflect.TagK4[F],
    isNotIntersection: IsNotIntersection[F[_, _, _, _]]
  ): DeriveTag4[F] =
    DeriveTag4[F](typeTag, isNotIntersection)
}

final case class DeriveTag5[F[_, _, _, _, _]](
  typeTag: izumi.reflect.TagK5[F],
  isNotIntersection: IsNotIntersection[F[_, _, _, _, _]]
)

object DeriveTag5 {

  implicit def derive[F[_, _, _, _, _]](implicit
    typeTag: izumi.reflect.TagK5[F],
    isNotIntersection: IsNotIntersection[F[_, _, _, _, _]]
  ): DeriveTag5[F] =
    DeriveTag5[F](typeTag, isNotIntersection)
}
