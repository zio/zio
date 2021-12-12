package zio.internal.metrics

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.{AtomicLongArray, DoubleAdder, LongAdder}

private[zio] sealed abstract class ConcurrentHistogram {

  // The overall count for all observed values in the histogram
  def getCount(): Long

  // Observe a single value
  def observe(value: Double): Unit

  // Create a Snaphot (Boundary, Sum of all observed values for the bucket with that boundary)
  def snapshot(): Chunk[(Double, Long)]

  // The sum of all observed values
  def getSum(): Double
}

private[zio] object ConcurrentHistogram {

  def manual(bounds: Chunk[Double]): ConcurrentHistogram =
    new ConcurrentHistogram {
      private[this] val values     = new AtomicLongArray(bounds.length + 1)
      private[this] val boundaries = Array.ofDim[Double](bounds.length)
      private[this] val count      = new LongAdder
      private[this] val sum        = new DoubleAdder
      private[this] val size       = bounds.length
      bounds.sorted.zipWithIndex.foreach { case (n, i) => boundaries(i) = n }

      def getCount(): Long = count.longValue()

      // Insert the value into the right bucket with a binary search
      def observe(value: Double): Unit = {
        var from = 0
        var to   = size
        while (from != to) {
          val mid      = from + (to - from) / 2
          val boundary = boundaries(mid)
          if (value <= boundary) to = mid else from = mid

          // The special case when to / from have a distance of one
          if (to == from + 1) {
            if (value <= boundaries(from)) to = from else from = to
          }
        }
        values.getAndIncrement(from)
        count.increment()
        sum.add(value)
        ()
      }

      def snapshot(): Chunk[(Double, Long)] = {
        val builder   = ChunkBuilder.make[(Double, Long)]()
        var i         = 0
        var cumulated = 0L
        while (i != size) {
          val boundary = boundaries(i)
          val value    = values.get(i)
          cumulated += value
          builder += boundary -> cumulated
          i += 1
        }
        builder.result()
      }

      def getSum(): Double = sum.doubleValue()
    }
}
