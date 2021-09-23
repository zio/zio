package zio.internal.metrics

import zio._

import java.util.concurrent.atomic.{AtomicInteger, AtomicReferenceArray, DoubleAdder, LongAdder}

private[zio] sealed abstract class ConcurrentSummary {

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

private[zio] object ConcurrentSummary {

  def manual(maxSize: Int, maxAge: Duration, err: Double, quantiles: Chunk[Double]): ConcurrentSummary =
    new ConcurrentSummary with ConcurrentSummaryBase {
      private[this] val values                     = new AtomicReferenceArray[(java.time.Instant, Double)](maxSize)
      private[this] val head                       = new AtomicInteger(0)
      private[this] val count                      = new LongAdder
      private[this] val sum                        = new DoubleAdder
      protected val sortedQuantiles: Chunk[Double] = quantiles.sorted(DoubleOrdering)
      protected val error: Double                  = err

      override def toString = s"ConcurrentSummary.manual(${getCount()}, ${getSum()})"

      def getCount(): Long =
        count.longValue

      def getSum(): Double =
        sum.doubleValue

      // Just before the Snapshot we filter out all values older than maxAge
      def snapshot(now: java.time.Instant): Chunk[(Double, Option[Double])] = {
        val builder = ChunkBuilder.make[Double]()

        // If the buffer is not full yet it contains valid items at the 0..last indices
        // and null values at the rest of the positions.
        // If the buffer is already full then all elements contains a valid measurement with timestamp.
        // At any given point in time we can enumerate all the non-null elements in the buffer and filter
        // them by timestamp to get a valid view of a time window.
        // The order does not matter because it gets sorted before passing to calculateQuantiles.

        for (idx <- 0 until maxSize) {
          val item = values.get(idx)
          if (item != null) {
            val (t, v) = item
            val age    = Duration.fromInterval(t, now)
            if (!age.isNegative && age.compareTo(maxAge) <= 0) {
              builder += v
            }
          }
        }

        calculateQuantiles(builder.result().sorted(DoubleOrdering))
      }

      // Assuming that the instant of observed values is continuously increasing
      // While Observing we cut off the first sample if we have already maxSize samples
      def observe(value: Double, t: java.time.Instant): Unit = {
        if (maxSize > 0) {
          val target = head.incrementAndGet() % maxSize
          values.set(target, (t, value))
        }

        count.increment()
        sum.add(value)
        ()
      }
    }
}
