package scalaz.zio.lockfree.impls

import scalaz.zio.lockfree.MutableConcurrentQueue

class JCToolsQueue[A](desiredCapacity: Int) extends MutableConcurrentQueue[A] {
  private val jctools = new org.jctools.queues.MpmcArrayQueue[A](desiredCapacity)

  override val capacity: Int = jctools.capacity()

  override def size(): Int = jctools.size()

  override def enqueuedCount(): Long = jctools.currentProducerIndex()

  override def dequeuedCount(): Long = jctools.currentConsumerIndex()

  override def offer(a: A): Boolean = jctools.offer(a)

  override def poll(): Option[A] = Option(jctools.poll())

  override def isEmpty(): Boolean = jctools.isEmpty()

  override def isFull(): Boolean =
    jctools.currentConsumerIndex() + jctools.capacity() - 1 == jctools.currentProducerIndex()
}
