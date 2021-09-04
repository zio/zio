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

package zio.metrics

import zio._
import zio.internal.metrics._

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
