package scalaz.zio.internal.impls

import java.util.concurrent.ConcurrentLinkedQueue
import scalaz.zio.internal.MutableConcurrentQueue
import java.util.concurrent.atomic.AtomicLong

class LinkedQueue[A] extends MutableConcurrentQueue[A] {
  override final val capacity: Int = Int.MaxValue

  private[this] val jucConcurrentQueue = new ConcurrentLinkedQueue[A]()

  /*
   * Using increment on AtomicLongs to provide metrics '''will''' have
   * performance implications. Having a better solution would be
   * desirable.
   */
  private[this] val enqueuedCounter = new AtomicLong(0)
  private[this] val dequeuedCounter = new AtomicLong(0)

  override final def size(): Int = jucConcurrentQueue.size()

  override final def enqueuedCount(): Long = enqueuedCounter.get()

  override final def dequeuedCount(): Long = dequeuedCounter.get()

  override final def offer(a: A): Boolean = {
    val success = jucConcurrentQueue.offer(a)
    if (success) enqueuedCounter.incrementAndGet()
    success
  }

  override final def poll(default: A): A = {
    val polled = jucConcurrentQueue.poll()
    if (polled != null) {
      dequeuedCounter.incrementAndGet()
      polled
    } else default
  }

  override final def isEmpty(): Boolean = jucConcurrentQueue.isEmpty

  override final def isFull(): Boolean = false
}
