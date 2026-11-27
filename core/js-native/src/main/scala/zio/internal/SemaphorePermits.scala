/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.internal

import java.util.concurrent.atomic.AtomicLong

/**
 * The free-permit counter of a `zio.Semaphore`. The JVM version pads the
 * counter onto a cache line of its own; here a plain atomic is enough.
 */
private[zio] abstract class SemaphorePermits(initial: Long) extends Serializable {
  private[this] val permitsValue = new AtomicLong(initial)

  protected final def permitsGet(): Long = permitsValue.get

  protected final def permitsCompareAndSet(expected: Long, updated: Long): Boolean =
    permitsValue.compareAndSet(expected, updated)

  protected final def permitsAddAndGet(delta: Long): Long = permitsValue.addAndGet(delta)
}
