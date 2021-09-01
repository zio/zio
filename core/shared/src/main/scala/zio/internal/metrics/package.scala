package zio.internal

package object metrics {

  private[zio] lazy val metricState: ConcurrentState =
    new ConcurrentState

  val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)
}
