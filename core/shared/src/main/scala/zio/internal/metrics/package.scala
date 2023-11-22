package zio.internal

import zio.Chunk

import zio.stacktracer.TracingImplicits.disableAutoTrace
import scala.annotation.tailrec

package object metrics {

  private[zio] val metricRegistry: ConcurrentMetricRegistry =
    new ConcurrentMetricRegistry

  private[metrics] val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)

  private[zio] def calculateQuantiles(
    sortedQuantiles: Chunk[Double],
    sortedSamples: Chunk[Double]
  ): Chunk[(Double, Option[Double])] = {
    val length = sortedSamples.length
    if (length == 0) sortedQuantiles.map((_, None))
    else {
      sortedQuantiles.map { quantile =>
        if (quantile <= 0.0) (quantile, Some(sortedSamples(0)))
        else if (quantile >= 1.0) (quantile, Some(sortedSamples(length - 1)))
        else {
          val index = math.ceil(quantile * length).toInt - 1
          val value = sortedSamples(index)
          (quantile, Some(value))
        }
      }
    }
  }

  private[metrics] case class ResolvedQuantile(
    quantile: Double,      // The Quantile that shall be resolved
    value: Option[Double], // Some(d) if a value for the quantile could be found, None otherwise
    consumed: Int,         // How many samples have been consumed before this quantile
    rest: Chunk[Double]    // The rest of the samples after the quantile has been resolved
  )
}
