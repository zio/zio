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

sealed trait MetricKeyType {
  type In
  type Out
}
object MetricKeyType {
  type Counter = Counter.type

  case object Counter extends MetricKeyType {
    type In  = Double
    type Out = MetricState2.Counter
  }

  type Gauge = Gauge.type
  case object Gauge extends MetricKeyType {
    type In  = Double
    type Out = MetricState2.Gauge
  }

  final case class Histogram(
    boundaries: ZIOMetric.Histogram.Boundaries
  ) extends MetricKeyType {
    type In  = Double
    type Out = MetricState2.Histogram
  }

  final case class Summary(
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ) extends MetricKeyType {
    type In  = Double
    type Out = MetricState2.Summary
  }

  final case class SetCount(setTag: String) extends MetricKeyType {
    type In  = String
    type Out = MetricState2.SetCount
  }
}
