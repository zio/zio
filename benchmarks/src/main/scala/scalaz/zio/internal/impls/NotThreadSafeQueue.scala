package scalaz.zio.internal.impls

import scalaz.zio.internal.MutableConcurrentQueue

class NotThreadSafeQueue[A](override val capacity: Int) extends MutableConcurrentQueue[A] {
  private val buf: Array[AnyRef] = Array.ofDim[AnyRef](capacity)
  var head: Long                 = 0
  var tail: Long                 = 0

  override def offer(a: A): Boolean =
    if (isFull()) {
      false
    } else {
      buf((tail % capacity).asInstanceOf[Int]) = a.asInstanceOf[AnyRef]
      tail += 1
      true
    }

  override def poll(default: A): A = {
    val idx = (head % capacity).toInt

    val el = buf(idx)
    if (el == null) {
      default
    } else {
      buf(idx) = null
      head += 1
      el.asInstanceOf[A]
    }
  }

  override def isEmpty(): Boolean =
    head == tail

  override def isFull(): Boolean =
    head + capacity == tail

  override def enqueuedCount(): Long = tail

  override def dequeuedCount(): Long = head

  override def size(): Int = (tail - head).toInt
}
