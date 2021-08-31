package zio.internal

package object metrics {

  private[zio] lazy val metricState: ConcurrentState =
    new ConcurrentState

  val dblOrdering: Ordering[Double] =
    ???
}
