/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

package zio.metrics.clients

import zio._

/**
 * A `MetricKey` is a unique key associated with each metric. The key is based
 * on a combination of the metric type, the name and labels associated with
 * the metric, and any other information to describe a a metric, such as the
 * boundaries of a histogram. In this way, it is impossible to ever create
 * metrics with conflicting keys.
 */
sealed trait MetricKey

object MetricKey {
  final case class Counter(name: String, tags: Chunk[MetricLabel] = Chunk.empty) extends MetricKey
  final case class Gauge(name: String, tags: Chunk[MetricLabel] = Chunk.empty)   extends MetricKey
  final case class Histogram(name: String, boundaries: Chunk[Double], tags: Chunk[MetricLabel] = Chunk.empty)
      extends MetricKey
  final case class Summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Chunk[MetricLabel] = Chunk.empty
  ) extends MetricKey
  final case class SetCount(name: String, setTag: String, tags: Chunk[MetricLabel] = Chunk.empty) extends MetricKey {
    def counterKey(word: String): MetricKey.Counter = MetricKey.Counter(name, Chunk(MetricLabel(setTag, word)) ++ tags)
  }
}
