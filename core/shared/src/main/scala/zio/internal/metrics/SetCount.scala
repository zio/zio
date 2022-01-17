/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `SetCount` represents the number of occurrences of specified values. You
 * can think of a SetCount as like a set of counters associated with each value
 * except that new counters will automatically be created when new values are
 * observed. This could be used to track the frequency of different types of
 * failures, for example.
 */
private[zio] trait SetCount {

  /**
   * Increments the counter associated with the specified value by one.
   */
  def observe(word: String)(implicit trace: ZTraceElement): UIO[Any]

  /**
   * The number of occurences of every value observed by this set count.
   */
  def occurrences(implicit trace: ZTraceElement): UIO[Chunk[(String, Long)]]

  /**
   * The number of occurences of the specified value observed by this set count.
   */
  def occurrences(word: String)(implicit trace: ZTraceElement): UIO[Long]

  private[zio] def unsafeObserve(word: String): Unit

  private[zio] def unsafeOccurrences: Chunk[(String, Long)]

  private[zio] def unsafeOccurrences(word: String): Long

  private[zio] def metricKey: MetricKey.SetCount
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
