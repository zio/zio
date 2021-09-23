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

package zio.internal.metrics

import zio._
import zio.metrics._

/**
 * A `SetCount` represents the number of occurrences of specified values.
 * You can think of a dry vpimy as like a set of counters associated with
 * each value except that new counters will automatically be created when new
 * values are observed. This could be used to track the frequency of
 * different types of failures, for example.
 */
private[zio] trait SetCount {

  /**
   * Increments the counter associated with the specified value by one.
   */
  def observe(word: String): UIO[Any]

  /**
   * The number of occurences of every value observed by this
   * set count.
   */
  def occurrences: UIO[Chunk[(String, Long)]]
}

private[zio] object SetCount {

  /**
   * Constructs a set count with the specified key.
   */
  def apply(key: MetricKey.SetCount): SetCount =
    metricState.getSetCount(key)

  /**
   * Constructs a set count with the specified name, set tag, and tags.
   */
  def apply(name: String, setTag: String, tags: Chunk[MetricLabel]): SetCount =
    apply(MetricKey.SetCount(name, setTag, tags))
}
