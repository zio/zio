package zio

/**
 * Proxies for annotations that exist in Scala 3 but not in Scala 2
 */
private[zio] object Scala3Annotations {
  type threadUnsafe = scala.annotation.threadUnsafe
}
