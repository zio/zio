package scalaz.zio.lockfree.impls

import java.util.concurrent.ConcurrentLinkedQueue

import scalaz.zio.lockfree.LockFreeQueue

class JucConcurrentQueue[A] extends LockFreeQueue[A] {
  override val capacity: Int = Int.MaxValue

  private val jucConcurrentQueue = new ConcurrentLinkedQueue[A]()

  override def relaxedSize(): Int = jucConcurrentQueue.size()

  override def enqueuedCount(): Long = throw new UnsupportedOperationException("enqueuedCount not implemented")

  override def dequeuedCount(): Long = throw new UnsupportedOperationException("dequeuedCount not implemented")

  override def offer(a: A): Boolean = jucConcurrentQueue.offer(a)

  override def poll(): Option[A] = Option(jucConcurrentQueue.poll())

  override def isEmpty(): Boolean = jucConcurrentQueue.isEmpty

  override def isFull(): Boolean = false
}
