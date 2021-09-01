package zio.internal.metrics

import zio._

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.{DoubleAdder, LongAdder}

sealed abstract class ConcurrentSummary {

  // The count how many values have been observed in total
  // It is NOT the number of samples currently held => count() >= samples.size
  def getCount(): Long

  // Observe a single value and record it in the summary
  def observe(value: Double, t: java.time.Instant): Unit

  // Create a snapshot
  // - The error margin
  // - Chunk of (Pair of (Quantile Boundary, Satisfying value if found))
  def snapshot(now: java.time.Instant): Chunk[(Double, Option[Double])]

  // The sum of all values ever observed
  def getSum(): Double

}

object ConcurrentSummary {

  def manual(maxSize: Int, maxAge: Duration, error: Double, quantiles: Chunk[Double]): ConcurrentSummary =
    new ConcurrentSummary {
      private[this] val values          = new ConcurrentLinkedDeque[(java.time.Instant, Double)]
      private[this] val count           = new LongAdder
      private[this] val currentCount    = new LongAdder
      private[this] val sum             = new DoubleAdder
      private[this] val sortedQuantiles = quantiles.sorted(DoubleOrdering)

      override def toString = s"ConcurrentSummary.manual(${getCount()}, ${getSum()})"

      def getCount(): Long =
        count.longValue

      def getSum(): Double =
        sum.doubleValue

      // Just before the Snapshot we filter out all values older than maxAge
      def snapshot(now: java.time.Instant): Chunk[(Double, Option[Double])] = {
        val builder = ChunkBuilder.make[Double]()

        val predicate: java.util.function.Predicate[(java.time.Instant, Double)] =
          new java.util.function.Predicate[(java.time.Instant, Double)] {
            override def test(t: (java.time.Instant, Double)): Boolean =
              Duration.fromInterval(t._1, now).compareTo(maxAge) > 0
          }

        values.removeIf(predicate)
        currentCount.reset()

        val it = values.iterator()
        while (it.hasNext()) {
          val (_, v) = it.next()
          currentCount.increment()
          builder += v
        }

        calculateQuantiles(builder.result().sorted(DoubleOrdering))
      }

      // Assuming that the instant of observed values is continuously increasing
      // While Observing we cut off the first sample if we have already maxSize samples
      def observe(value: Double, t: java.time.Instant): Unit = {
        if (currentCount.intValue() == maxSize) {
          values.removeFirst()
        } else {
          currentCount.increment()
        }
        values.add((t, value))

        count.increment()
        sum.add(value)
        ()
      }

      private def calculateQuantiles(
        sortedSamples: Chunk[Double]
      ): Chunk[(Double, Option[Double])] = {

        // The number of the samples examined
        val sampleCnt = sortedSamples.size

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

      private case class ResolvedQuantile(
        quantile: Double,      // The Quantile that shall be resolved
        value: Option[Double], // Some(d) if a value for the quantile could be found, None otherwise
        consumed: Int,         // How many samples have been consumed before this quantile
        rest: Chunk[Double]    // The rest of the samples after the quantile has been resolved
      )
    }
}
