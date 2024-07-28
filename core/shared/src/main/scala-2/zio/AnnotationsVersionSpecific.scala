package zio

import scala.annotation.StaticAnnotation

private[zio] trait AnnotationsVersionSpecific {
  final class static extends StaticAnnotation
}
