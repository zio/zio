package scalaz.zio.internal.impls

import java.io.Serializable
import java.util.concurrent.atomic.{ AtomicReference, LongAdder }

import scalaz.zio.internal.MutableConcurrentQueue

/**
 * This is a specialized implementation of MutableConcurrentQueue of
 * capacity 1. Since capacity 1 queues are by default used under the
 * hood in Streams as intermediate resource they should be very cheap
 * to create and throw away. Hence this queue is optimized (unlike
 * RingBuffer*) for a very small footprint, while still being plenty
 * fast.
 *
 * Allocating an object takes only 32 bytes + 16+ bytes in long adders (so 48+ bytes total),
 * which is 10x less that a smalles RingBuffer.
 *
 * scalaz.zio.internal.impls.OneElementConcurrentQueue object internals:
 *  OFFSET  SIZE                                          TYPE DESCRIPTION
 *       0     4                                               (object header)
 *       4     4                                               (object header)
 *       8     4                                               (object header)
 *      12     4                                           int OneElementConcurrentQueue.capacity
 *      16     4   java.util.concurrent.atomic.AtomicReference OneElementConcurrentQueue.ref
 *      20     4         java.util.concurrent.atomic.LongAdder OneElementConcurrentQueue.deqAdder
 *      24     4         java.util.concurrent.atomic.LongAdder OneElementConcurrentQueue.enqAdder
 *      28     4                                               (loss due to the next object alignment)
 * Instance size: 32 bytes
 * Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
 */
class OneElementConcurrentQueue[A] extends MutableConcurrentQueue[A] with Serializable {
  private[this] final val ref      = new AtomicReference[AnyRef]()
  private[this] final val deqAdder = new LongAdder()
  private[this] final val enqAdder = new LongAdder()

  override final val capacity: Int = 1

  override final def dequeuedCount(): Long = deqAdder.sum()
  override final def enqueuedCount(): Long = enqAdder.sum()

  override final def isEmpty(): Boolean = ref.get() == null
  override final def isFull(): Boolean  = !isEmpty()

  override final def offer(a: A): Boolean = {
    assert(a != null)

    val aRef    = ref
    var ret     = false
    var looping = true

    while (looping) {
      if (aRef.get() != null) looping = false
      else {
        if (aRef.compareAndSet(null, a.asInstanceOf[AnyRef])) {
          ret = true
          enqAdder.increment()
          looping = false
        }
      }
    }

    ret
  }

  override final def poll(default: A): A = {
    var ret     = default
    var looping = true
    val aRef    = ref
    var el      = null.asInstanceOf[AnyRef]

    while (looping) {
      el = aRef.get()
      if (el == null) looping = false
      else {
        if (aRef.compareAndSet(el, null)) {
          ret = el.asInstanceOf[A]
          deqAdder.increment()
          looping = false
        }
      }
    }

    ret
  }

  override final def size(): Int = if (isEmpty()) 0 else 1
}
