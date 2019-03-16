/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio.internal.impls

import java.io.Serializable

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong, AtomicReference }

import scalaz.zio.internal.MutableConcurrentQueue

class OneElementConcurrentQueue[A] extends MutableConcurrentQueue[A] with Serializable {
  private[this] final val ref = new AtomicReference[AnyRef]()

  private[this] final val headCounter   = new AtomicLong(0L)
  private[this] final val deqInProgress = new AtomicBoolean(false)

  private[this] final val tailCounter   = new AtomicLong(0L)
  private[this] final val enqInProgress = new AtomicBoolean(false)

  override final val capacity: Int = 1

  override final def dequeuedCount(): Long = headCounter.get()
  override final def enqueuedCount(): Long = tailCounter.get()

  override final def isEmpty(): Boolean = ref.get() == null
  override final def isFull(): Boolean  = !isEmpty()

  override final def offer(a: A): Boolean = {
    assert(a != null)

    var res     = false
    var looping = true

    while (looping) {
      if (isFull()) {
        looping = false
      } else {
        if (enqInProgress.compareAndSet(false, true)) { // get an exclusive right to offer
          if (ref.get() == null) {
            tailCounter.lazySet(tailCounter.get() + 1)
            ref.lazySet(a.asInstanceOf[AnyRef])
            res = true
          }

          enqInProgress.lazySet(false)
          looping = false
        }
      }
    }

    res
  }

  override final def poll(default: A): A = {
    var res     = default
    var looping = true

    while (looping) {
      if (isEmpty()) {
        looping = false
      } else {
        if (deqInProgress.compareAndSet(false, true)) { // get an exclusive right to poll
          val el = ref.get().asInstanceOf[A]

          if (el != null) {
            res = el
            headCounter.lazySet(headCounter.get() + 1)
            ref.lazySet(null.asInstanceOf[AnyRef])
          }

          deqInProgress.lazySet(false)
          looping = false
        }
      }
    }

    res
  }

  override final def size(): Int = if (isEmpty()) 0 else 1
}
