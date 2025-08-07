package zio

import scala.annotation.StaticAnnotation

/**
 * Stubs for annotations that exist in Scala 3 but not in Scala 2
 */
private[zio] object Scala3Annotations {
  final class threadUnsafe extends StaticAnnotation
}
