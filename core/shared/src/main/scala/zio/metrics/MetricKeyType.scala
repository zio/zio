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

package zio.metrics

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The type of a metric: counter, frequency, gauge, histogram or summary, with
 * the settings of the metric (the boundaries of a histogram, the window and
 * quantiles of a summary). `In` is the type of the updates the metric accepts
 * and `Out` the type of its state.
 */
sealed trait MetricKeyType {

  /**
   * The type of the updates that a metric of this type accepts.
   */
  type In

  /**
   * The type of the state of a metric of this type.
   */
  type Out
}

/**
 * The five primitive metric types.
 */
object MetricKeyType {

  /**
   * A metric type whose updates have type `T`.
   */
  type WithIn[T] = MetricKeyType {
    type In = T
  }

  /**
   * The type of the `Counter` metric type.
   */
  type Counter = Counter.type

  /**
   * The counter metric type: updates are doubles added to the count.
   */
  case object Counter extends MetricKeyType {

    /**
     * The amount to add to the count.
     */
    type In = Double

    /**
     * The state of a counter.
     */
    type Out = MetricState.Counter
  }

  /**
   * The type of the `Frequency` metric type.
   */
  type Frequency = Frequency.type

  /**
   * The frequency metric type: updates are strings whose occurrences are
   * counted.
   */
  case object Frequency extends MetricKeyType {

    /**
     * The string whose occurrence is counted.
     */
    type In = String

    /**
     * The state of a frequency.
     */
    type Out = MetricState.Frequency
  }

  /**
   * The type of the `Gauge` metric type.
   */
  type Gauge = Gauge.type

  /**
   * The gauge metric type: updates are doubles that set or adjust the value.
   */
  case object Gauge extends MetricKeyType {

    /**
     * The value to set, or the amount to add.
     */
    type In = Double

    /**
     * The state of a gauge.
     */
    type Out = MetricState.Gauge
  }

  /**
   * The histogram metric type, with the upper boundaries of its buckets.
   */
  final case class Histogram(
    boundaries: Histogram.Boundaries
  ) extends MetricKeyType {

    /**
     * The value to observe.
     */
    type In = Double

    /**
     * The state of a histogram.
     */
    type Out = MetricState.Histogram
  }

  /**
   * Bucket boundaries for histograms.
   */
  object Histogram {

    /**
     * The upper boundaries of the buckets of a histogram. `fromChunk`, `linear`
     * and `exponential` keep only positive values and add `Double.MaxValue` as
     * the last boundary. `Boundaries(values)` takes the values as they are; a
     * value above the highest boundary is then counted in the `count` of the
     * state but in no bucket.
     *
     * @see
     *   [[https://zio.dev/reference/observability/metrics/histogram/ Histogram]]
     */
    final case class Boundaries(values: Chunk[Double])

    /**
     * Constructors for histogram boundaries.
     */
    object Boundaries {

      /**
       * Creates boundaries from the specified values: it keeps the values
       * greater than 0, adds `Double.MaxValue`, and removes duplicates while
       * keeping the order of the values. An empty chunk gives the single
       * boundary `Double.MaxValue`.
       */
      def fromChunk(chunk: Chunk[Double]): Boundaries =
        Boundaries((chunk.filter(_ > 0) ++ Chunk(Double.MaxValue)).distinct)

      /**
       * A helper method to create histogram bucket boundaries for a histogram
       * with linear increasing values: `start + i * width` for `i` from 0 until
       * `count`, passed through `fromChunk`. For example, `linear(0, 10, 3)`
       * gives `Chunk(10.0, 20.0, Double.MaxValue)`, because `fromChunk` drops
       * the boundary 0.
       */
      def linear(start: Double, width: Double, count: Int): Boundaries =
        fromChunk(Chunk.fromArray(0.until(count).map(i => start + i * width).toArray))

      /**
       * A helper method to create histogram bucket boundaries for a histogram
       * with exponentially increasing values: `count` values that start at
       * `start` and are each multiplied by `factor`, passed through
       * `fromChunk`. For example, `exponential(1, 2, 4)` gives `Chunk(1.0, 2.0,
       * 4.0, 8.0, Double.MaxValue)`.
       */
      def exponential(start: Double, factor: Double, count: Int): Boundaries =
        fromChunk(Chunk.iterate(start, count)(_ * factor))
    }
  }

  /**
   * The summary metric type: it keeps at most `maxSize` samples, reports the
   * `quantiles` of the samples that are at most `maxAge` old, and carries
   * `error` to its state.
   */
  final case class Summary(
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ) extends MetricKeyType {

    /**
     * The value to observe, with the time of the observation.
     */
    type In = (Double, java.time.Instant)

    /**
     * The state of a summary.
     */
    type Out = MetricState.Summary
  }
}
