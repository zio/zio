package zio.internal.metrics

import zio._
import zio.internal.ZMetrics._

/**
 * A SetCount ia a metric that counts the number of occurences of Strings. The individual
 * values are not known up front. Basically the SetCount is a dynamic set of counters,
 * one counter for each unique word observed.
 */
trait SetCount {

  /**
   * Increment the counter for a given word by 1
   */
  def observe(word: String): UIO[Any]
}

object SetCount {

  def apply(key: MetricKey.SetCount): SetCount =
    metricState.getSetCount(key)

  def apply(name: String, setTag: String, tags: Label*): SetCount =
    apply(MetricKey.SetCount(name, setTag, Chunk.fromIterable(tags)))

  val none: SetCount =
    new SetCount {
      def observe(word: String): UIO[Any] = ZIO.unit
    }
}
