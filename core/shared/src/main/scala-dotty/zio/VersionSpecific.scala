package zio

import scala.reflect.ClassTag

private[zio] trait VersionSpecific {

  // Workaround for disabled summon for ClassTag[Nothing]
  // see discussion in https://github.com/zio/zio/pull/3136
  sealed trait Tagged[A]
  case object TaggedNothingType extends Tagged[Nothing]
  final case class TaggedInstantiableType[A](cls: ClassTag[A]) extends Tagged[A]

  object Tagged extends TaggedLowPrio {
    implicit def taggedInstantiable[T](implicit tag: ClassTag[T]): Tagged[T] = TaggedInstantiableType(tag)
  }

  abstract class TaggedLowPrio {
    implicit val taggedNothing: Tagged[Nothing] = TaggedNothingType
  }

  sealed trait TagType
  case object TagNothingType                    extends TagType
  case class TagInstantiableType(cls: Class[_]) extends TagType

  private[zio] def taggedIsSubtype[A, B](left: TagType, right: TagType): Boolean =
    (left, right) match {
      case (TagNothingType, _) => true
      case (_, TagNothingType) => false
      case (TagInstantiableType(leftClass), TagInstantiableType(rightClass)) =>
        // assignability and subtyping are diffrent concepts and so this implementation
        // is broken for some cases like Tuples, Lists, etc
        // TODO: add dotty support for izumi-reflect and replace ClassTag
        rightClass.isAssignableFrom(leftClass)
    }

  private[zio] def taggedTagType[A](tagged: Tagged[A]): TagType =
    tagged match {
      case TaggedNothingType           => TagNothingType
      case TaggedInstantiableType(cls) => TagInstantiableType(cls.runtimeClass)
    }

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] = {
    val _ = t
    Set()
  }
}
