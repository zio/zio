package zio.metrics.jvm

import scala.collection.JavaConverters._

trait JvmMetricsVersionSpecific {
  def fromJavaList[A](jlist: java.util.List[A]): Iterable[A] =
    jlist.asScala

  def fromJavaSet[A](jset: java.util.Set[A]): Iterable[A] =
    jset.asScala
}
