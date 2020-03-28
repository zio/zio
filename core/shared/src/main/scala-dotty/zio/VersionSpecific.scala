package zio

private[zio] trait VersionSpecific {

  type Tagged[A] = scala.reflect.ClassTag[A]
  type TagType   = Class[_]

  private[zio] def taggedTagType[A](tagged: Tagged[A]): TagType =
    tagged.runtimeClass

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] = {
    val _ = t
    Set()
  }
}
