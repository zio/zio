package scalaz.zio.internal.impls

import java.util.concurrent.ConcurrentLinkedQueue
import scalaz.zio.internal.MutableConcurrentQueue
import java.util.concurrent.atomic.AtomicLong

class JucCLQ[A] extends MutableConcurrentQueue[A] with Serializable {
  override val capacity: Int = Int.MaxValue

  private val jucConcurrentQueue = new ConcurrentLinkedQueue[A]()

  /*
   * Using increment on AtomicLongs to provide metrics '''will''' have
   * performance implications. Having a better solution would be
   * desirable.
   */
  private val enqueuedCounter = new AtomicLong(0)
  private val dequeuedCounter = new AtomicLong(0)

  override def size(): Int = jucConcurrentQueue.size()

  override def enqueuedCount(): Long = enqueuedCounter.get()

  override def dequeuedCount(): Long = dequeuedCounter.get()

  override def offer(a: A): Boolean = {
    val success = jucConcurrentQueue.offer(a)
    if (success) enqueuedCounter.incrementAndGet()
    success
  }

  override def poll(default: A): A = {
    val polled = jucConcurrentQueue.poll()
    if (polled != null) {
      dequeuedCounter.incrementAndGet()
      polled
    } else default
  }

  override def isEmpty(): Boolean = jucConcurrentQueue.isEmpty

  override def isFull(): Boolean = false
}
