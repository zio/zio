package scalaz.zio.lockfree.impls

import java.util.concurrent.ConcurrentLinkedQueue

import scalaz.zio.lockfree.LockFreeQueue

class JucQueue[A] extends LockFreeQueue[A] {
  override val capacity: Int = Int.MaxValue

  private val jucConcurrentQueue = new ConcurrentLinkedQueue[A]()

  override def relaxedSize(): Int = jucConcurrentQueue.size()

  override def enqueuedCount(): Long = 0

  override def dequeuedCount(): Long = 0

  override def offer(a: A): Boolean = jucConcurrentQueue.offer(a)

  override def poll(): Option[A] = Option(jucConcurrentQueue.poll())

  override def isEmpty(): Boolean = jucConcurrentQueue.isEmpty

  override def isFull(): Boolean = false
}
