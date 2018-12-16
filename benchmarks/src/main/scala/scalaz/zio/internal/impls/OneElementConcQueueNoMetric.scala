package scalaz.zio.internal.impls

import java.util.concurrent.atomic.AtomicReference

import scalaz.zio.internal.MutableConcurrentQueue

class OneElementConcQueueNoMetric[A] extends MutableConcurrentQueue[A] {
  private[this] final val ref = new AtomicReference[AnyRef]()
  val capacity: Int           = 1

  def dequeuedCount(): Long = throw new NotImplementedError("dequeuedCount is not supported")
  def enqueuedCount(): Long = throw new NotImplementedError("enqueuedCount is not supported")

  def isEmpty(): Boolean = ref.get() == null
  def isFull(): Boolean  = !isEmpty()

  def offer(a: A): Boolean = {
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

  def poll(default: A): A = {
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

  def size(): Int = if (isEmpty()) 0 else 1
}
