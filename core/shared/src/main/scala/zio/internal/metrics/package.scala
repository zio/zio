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
    error: Double,
    sortedQuantiles: Chunk[Double],
    sortedSamples: Chunk[Double]
  ): Chunk[(Double, Option[Double])] = {

    // The number of the samples examined
    val sampleCnt = sortedSamples.size

    @tailrec
    def get(
      current: Option[Double],
      consumed: Int,
      q: Double,
      rest: Chunk[Double]
    ): ResolvedQuantile =
      rest match {
        // if the remaining list of samples is empty there is nothing more to resolve
        case c if c.isEmpty => ResolvedQuantile(q, None, consumed, Chunk.empty)
        // if the quantile is the 100% Quantile, we can take the max of all remaining values as the result
        case c if q == 1.0d => ResolvedQuantile(q, Some(c.last), consumed + c.length, Chunk.empty)
        case c              =>
          // Split in 2 chunks, the first chunk contains all elements of the same value as the chunk head
          val sameHead = c.splitWhere(_ > c.head)
          // How many elements do we want to accept for this quantile
          val desired = q * sampleCnt
          // The error margin
          val allowedError = error / 2 * desired
          // Taking into account the elements consumed from the samples so far and the number of
          // same elements at the beginning of the chunk
          // calculate the number of elements we would have if we selected the current head as result
          val candConsumed = consumed + sameHead._1.length
          val candError    = Math.abs(candConsumed - desired)

          // If we haven't got enough elements yet, recurse
          if (candConsumed < desired - allowedError)
            get(c.headOption, candConsumed, q, sameHead._2)
          // If we have too many elements, select the previous value and hand back the the rest as leftover
          else if (candConsumed > desired + allowedError) ResolvedQuantile(q, current, consumed, c)
          // If we are in the target interval, select the current head and hand back the leftover after dropping all elements
          // from the sample chunk that are equal to the current head
          else {
            current match {
              case None => get(c.headOption, candConsumed, q, sameHead._2)
              case Some(current) =>
                val prevError = Math.abs(desired - current)
                if (candError < prevError) get(c.headOption, candConsumed, q, sameHead._2)
                else ResolvedQuantile(q, Some(current), consumed, rest)
            }
          }
      }

    val resolved = sortedQuantiles match {
      case e if e.isEmpty => Chunk.empty
      case c =>
        sortedQuantiles.tail
          .foldLeft(Chunk(get(None, 0, c.head, sortedSamples))) { case (cur, q) =>
            cur ++ Chunk(get(cur.head.value, cur.head.consumed, q, cur.head.rest))
          }
    }

    resolved.map(rq => (rq.quantile, rq.value))
  }

  private[metrics] case class ResolvedQuantile(
    quantile: Double,      // The Quantile that shall be resolved
    value: Option[Double], // Some(d) if a value for the quantile could be found, None otherwise
    consumed: Int,         // How many samples have been consumed before this quantile
    rest: Chunk[Double]    // The rest of the samples after the quantile has been resolved
  )
}
