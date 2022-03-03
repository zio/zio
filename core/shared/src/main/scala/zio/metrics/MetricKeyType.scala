/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

sealed trait MetricKeyType {
  type In
  type Out

  def fold[Z](
    isCounter: (In => Double, Out => MetricState.Counter) => Z,
    isGauge: (In => Double, Out => MetricState.Gauge) => Z,
    isHistogram: (In => Double, Out => MetricState.Histogram) => Z,
    isSummary: (In => Double, Out => MetricState.Summary) => Z,
    isSetCount: (In => String, Out => MetricState.SetCount) => Z
  ): Z
}
object MetricKeyType {
  type Counter = Counter.type

  case object Counter extends MetricKeyType {
    type In  = Double
    type Out = MetricState.Counter

    def fold[Z](
      isCounter: (In => Double, Out => MetricState.Counter) => Z,
      isGauge: (In => Double, Out => MetricState.Gauge) => Z,
      isHistogram: (In => Double, Out => MetricState.Histogram) => Z,
      isSummary: (In => Double, Out => MetricState.Summary) => Z,
      isSetCount: (In => String, Out => MetricState.SetCount) => Z
    ): Z = isCounter(identity(_), identity(_))
  }

  type Gauge = Gauge.type
  case object Gauge extends MetricKeyType {
    type In  = Double
    type Out = MetricState.Gauge

    def fold[Z](
      isCounter: (In => Double, Out => MetricState.Counter) => Z,
      isGauge: (In => Double, Out => MetricState.Gauge) => Z,
      isHistogram: (In => Double, Out => MetricState.Histogram) => Z,
      isSummary: (In => Double, Out => MetricState.Summary) => Z,
      isSetCount: (In => String, Out => MetricState.SetCount) => Z
    ): Z = isGauge(identity(_), identity(_))
  }

  final case class Histogram(
    boundaries: Histogram.Boundaries
  ) extends MetricKeyType {
    type In  = Double
    type Out = MetricState.Histogram

    def fold[Z](
      isCounter: (In => Double, Out => MetricState.Counter) => Z,
      isGauge: (In => Double, Out => MetricState.Gauge) => Z,
      isHistogram: (In => Double, Out => MetricState.Histogram) => Z,
      isSummary: (In => Double, Out => MetricState.Summary) => Z,
      isSetCount: (In => String, Out => MetricState.SetCount) => Z
    ): Z = isHistogram(identity(_), identity(_))
  }

  object Histogram {
    final case class Boundaries(values: Chunk[Double])

    object Boundaries {

      def fromChunk(chunk: Chunk[Double]): Boundaries = Boundaries((chunk ++ Chunk(Double.MaxValue)).distinct)

      /**
       * A helper method to create histogram bucket boundaries for a histogram
       * with linear increasing values
       */
      def linear(start: Double, width: Double, count: Int): Boundaries =
        fromChunk(Chunk.fromArray(0.until(count).map(i => start + i * width).toArray))

      /**
       * A helper method to create histogram bucket boundaries for a histogram
       * with exponentially increasing values
       */
      def exponential(start: Double, factor: Double, count: Int): Boundaries =
        fromChunk(Chunk.fromArray(0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toArray))
    }
  }

  final case class Summary(
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ) extends MetricKeyType {
    type In  = Double
    type Out = MetricState.Summary

    def fold[Z](
      isCounter: (In => Double, Out => MetricState.Counter) => Z,
      isGauge: (In => Double, Out => MetricState.Gauge) => Z,
      isHistogram: (In => Double, Out => MetricState.Histogram) => Z,
      isSummary: (In => Double, Out => MetricState.Summary) => Z,
      isSetCount: (In => String, Out => MetricState.SetCount) => Z
    ): Z = isSummary(identity(_), identity(_))
  }

  final case class SetCount(setTag: String) extends MetricKeyType {
    type In  = String
    type Out = MetricState.SetCount

    def fold[Z](
      isCounter: (In => Double, Out => MetricState.Counter) => Z,
      isGauge: (In => Double, Out => MetricState.Gauge) => Z,
      isHistogram: (In => Double, Out => MetricState.Histogram) => Z,
      isSummary: (In => Double, Out => MetricState.Summary) => Z,
      isSetCount: (In => String, Out => MetricState.SetCount) => Z
    ): Z = isSetCount(identity(_), identity(_))
  }
}
