/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal.metrics

import zio._
import zio.metrics._

import java.util.concurrent.atomic._
import java.util.concurrent.ConcurrentHashMap

private[zio] class ConcurrentMetricHooksPlatformSpecific extends ConcurrentMetricHooks {
  def counter(key: MetricKey.Counter): MetricHook.Counter = {
    val sum = new AtomicReference[Double](0.0)

    MetricHook(
      v => sum.updateAndGet(current => current + v),
      () => MetricState.Counter(sum.get()),
      v => sum.updateAndGet(current => current + v)
    )
  }

  def gauge(key: MetricKey.Gauge, startAt: Double): MetricHook.Gauge = {
    val value = new AtomicReference[Double](startAt)

    MetricHook(v => value.set(v), () => MetricState.Gauge(value.get()), v => value.updateAndGet(current => current + v))
  }

  def histogram(key: MetricKey.Histogram): MetricHook.Histogram = {
    val bounds     = key.keyType.boundaries.values
    val values     = new AtomicLongArray(bounds.length + 1)
    val boundaries = Array.ofDim[Double](bounds.length)
    val count      = new AtomicLong(0)
    val sum        = new AtomicReference[Double](0.0)
    val size       = bounds.length
    val min        = new AtomicReference[Double](Double.MaxValue)
    val max        = new AtomicReference[Double](Double.MinValue)

    bounds.sorted.zipWithIndex.foreach { case (n, i) => boundaries(i) = n }

    // Insert the value into the right bucket with a binary search
    val update = (value: Double) => {
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
      count.incrementAndGet()
      sum.updateAndGet(current => current + value)
      min.updateAndGet(current => Math.min(current, value))
      max.updateAndGet(current => Math.max(current, value))
      ()
    }

    def getBuckets(): Chunk[(Double, Long)] = {
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

    MetricHook(
      update,
      () => MetricState.Histogram(getBuckets(), count.get(), min.get(), max.get(), sum.get()),
      update
    )
  }

  def summary(key: MetricKey.Summary): MetricHook.Summary = {
    import key.keyType.{maxSize, maxAge, error, quantiles}

    val values = new AtomicReferenceArray[(Double, java.time.Instant)](maxSize)
    val head   = new AtomicLong(0)
    val count  = new AtomicLong(0)
    val sum    = new AtomicReference[Double](0.0)
    val min    = new AtomicReference[Double](Double.MaxValue)
    val max    = new AtomicReference[Double](Double.MinValue)

    val sortedQuantiles: Chunk[Double] = quantiles.sorted(DoubleOrdering)

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
        if (item ne null) {
          val (v, t) = item
          val age    = Duration.fromInterval(t, now)
          if (!age.isNegative && age.compareTo(maxAge) <= 0) {
            builder += v
          }
        }
      }

      zio.internal.metrics.calculateQuantiles(sortedQuantiles, builder.result().sorted(DoubleOrdering))
    }

    // Assuming that the instant of observed values is continuously increasing
    // While Observing we cut off the first sample if we have already maxSize samples
    def observe(tuple: (Double, java.time.Instant)): Unit = {
      if (maxSize > 0) {
        val target = (head.incrementAndGet() % maxSize).toInt
        values.set(target, tuple)
      }

      val value = tuple._1
      count.incrementAndGet()
      sum.updateAndGet(current => current + value)
      min.updateAndGet(current => Math.min(current, value))
      max.updateAndGet(current => Math.max(current, value))

      ()
    }

    MetricHook(
      observe(_),
      () =>
        MetricState.Summary(
          error,
          snapshot(java.time.Instant.now()),
          count.get(),
          min.get(),
          max.get(),
          sum.get()
        ),
      observe(_)
    )
  }

  def frequency(key: MetricKey.Frequency): MetricHook.Frequency = {
    val count  = new AtomicLong(0)
    val values = new ConcurrentHashMap[String, AtomicLong]

    val update = (word: String) => {
      count.incrementAndGet()
      var slot = values.get(word)
      if (slot eq null) {
        val cnt = new AtomicLong(0)
        values.putIfAbsent(word, cnt)
        slot = values.get(word)
      }
      slot.incrementAndGet()
      ()
    }

    def snapshot(): Map[String, Long] = {
      val builder = scala.collection.mutable.Map[String, Long]()
      val it      = values.entrySet().iterator()
      while (it.hasNext()) {
        val e = it.next()
        builder.update(e.getKey(), e.getValue().get())
      }

      builder.toMap
    }

    MetricHook(update, () => MetricState.Frequency(snapshot()), update)
  }

}
