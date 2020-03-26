package zio

import scala.reflect.ClassTag

private[zio] trait VersionSpecific {

  // Workaround for disabled summon for ClassTag[Nothing]
  // due to https://github.com/lampepfl/dotty/issues/1730
  final class ClassTagBox[A](val cls: ClassTag[A])

  object ClassTagBox extends LowPrio {
    implicit def fromClassTag[T](implicit tag: ClassTag[T]): ClassTagBox[T] = new ClassTagBox(tag)
  }

  abstract class LowPrio {
    implicit val classTagNothing: ClassTagBox[Nothing] = new ClassTagBox(ClassTag(classOf[Nothing]))
  }

  type Tagged[A] = ClassTagBox[A]
  type TagType   = Class[_]

  private[zio] def taggedIsSubtype[A, B](left: TagType, right: TagType): Boolean =
    right.isAssignableFrom(left)

  private[zio] def taggedTagType[A](tagged: Tagged[A]): TagType =
    tagged.cls.runtimeClass

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] = {
    val _ = t
    Set()
  }
}
