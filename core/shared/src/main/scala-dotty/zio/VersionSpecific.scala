package zio

private[zio] trait VersionSpecific {

  type TaggedType[A] = scala.reflect.ClassTag[A]
  type TagType       = scala.reflect.ClassTag[_]

  private[zio] def taggedIsSubtype[A, B](left: TagType, right: TagType): Boolean =
    right.runtimeClass.isAssignableFrom(left.runtimeClass)

  private[zio] def taggedTagType[A](tagged: Tagged[A]): TagType = tagged.tag

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] = {
    val _ = t
    Set()
  }
}
