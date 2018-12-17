package scalaz.zio.internal.impls

import java.util.concurrent.atomic.AtomicReference

import scalaz.zio.internal.MutableConcurrentQueue

class OneElementConcQueueNoMetric[A] extends MutableConcurrentQueue[A] {
  private[this] final val ref = new AtomicReference[AnyRef]()

  override final val capacity: Int           = 1

  override final def dequeuedCount(): Long =
    throw new NotImplementedError("dequeuedCount is not supported")
  override final def enqueuedCount(): Long =
    throw new NotImplementedError("enqueuedCount is not supported")

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
          looping = false
        }
      }
    }

    ret
  }

  override final def size(): Int = if (isEmpty()) 0 else 1
}
