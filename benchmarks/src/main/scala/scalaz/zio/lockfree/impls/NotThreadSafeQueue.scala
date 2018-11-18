package scalaz.zio.lockfree.impls

import scalaz.zio.lockfree.MutableConcurrentQueue

class NotThreadSafeQueue[A](override val capacity: Int) extends MutableConcurrentQueue[A] {
  private val buf: Array[AnyRef] = Array.ofDim[AnyRef](capacity)
  var head: Long                 = 0
  var tail: Long                 = 0

  override def offer(a: A): Boolean =
    if (isFull()) {
      false
    } else {
      tail += 1
      buf((tail % capacity).asInstanceOf[Int]) = a.asInstanceOf[AnyRef]
      true
    }

  override def poll(default: A): A =
    if (isEmpty()) {
      default
    } else {
      val el = buf((head % capacity).asInstanceOf[Int])
      head += 1
      el.asInstanceOf[A]
    }

  override def isEmpty(): Boolean =
    head == tail

  override def isFull(): Boolean =
    head + capacity - 1 == tail

  override def enqueuedCount(): Long = tail

  override def dequeuedCount(): Long = head

  override def size(): Int = capacity - (head - tail).toInt
}
