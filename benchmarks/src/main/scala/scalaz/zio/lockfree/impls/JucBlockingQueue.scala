package scalaz.zio.lockfree.impls

import java.util.concurrent.LinkedBlockingQueue

import scalaz.zio.lockfree.LockFreeQueue

class JucBlockingQueue[A] extends LockFreeQueue[A] {
  override val capacity: Int = Int.MaxValue

  private val jucBlockingQueue = new LinkedBlockingQueue[A](capacity)

  override def size(): Int = jucBlockingQueue.size()

  override def enqueuedCount(): Long = throw new UnsupportedOperationException("enqueuedCount not implemented")

  override def dequeuedCount(): Long = throw new UnsupportedOperationException("dequeuedCount not implemented")

  override def offer(a: A): Boolean = jucBlockingQueue.offer(a)

  override def poll(): Option[A] = Option(jucBlockingQueue.poll())

  override def isEmpty(): Boolean = jucBlockingQueue.isEmpty

  override def isFull(): Boolean = false
}
