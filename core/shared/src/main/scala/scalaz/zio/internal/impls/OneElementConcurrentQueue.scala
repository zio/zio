package scalaz.zio.internal.impls

import java.io.Serializable

import java.util.concurrent.atomic.{ AtomicReference, AtomicLong }

import scalaz.zio.internal.MutableConcurrentQueue

object OneElementConcurrentQueue {
  final val STATE_LOOP     = 0
  final val STATE_FULL     = 1
  final val STATE_EMPTY    = -1
  final val STATE_RESERVED = 2
}

class OneElementConcurrentQueue[A] extends MutableConcurrentQueue[A] with Serializable {
  import OneElementConcurrentQueue._

  private[this] val ref = new AtomicReference[AnyRef]()
  private[this] val headCounter = new AtomicLong(0L)
  private[this] val tailCounter = new AtomicLong(0L)

  val capacity: Int = 1

  def dequeuedCount(): Long = headCounter.get()
  def enqueuedCount(): Long = tailCounter.get()

  def isEmpty(): Boolean = ref.get() == null
  def isFull(): Boolean  = !isEmpty()

  def offer(a: A): Boolean = {
    assert(a != null)

    var state = STATE_LOOP
    val aTail = tailCounter

    val aHead   = headCounter
    var curHead = aHead.get()

    while (state == STATE_LOOP) {
      if (isFull() || curHead + 1 == aTail.get()) {
        state = STATE_FULL
      } else { // try to reserve the opportunity to offer
        if (aTail.compareAndSet(curHead, curHead + 1)) {
          ref.lazySet(a.asInstanceOf[AnyRef])
          state = STATE_RESERVED
        } else {
          curHead = aHead.get()
        }
      }
    }

    if (state == STATE_RESERVED) true
    else false
  }

  def poll(default: A): A = {
    var state = STATE_LOOP
    var el    = null.asInstanceOf[AnyRef]

    val aHead   = headCounter
    var curHead = aHead.get()

    val aTail = tailCounter

    while (state == STATE_LOOP) {
      if (isEmpty() || aTail.get() == curHead) {
        state = STATE_EMPTY
      } else {
        if (aHead.compareAndSet(curHead, curHead + 1)) {
          el = ref.get()
          ref.lazySet(null.asInstanceOf[AnyRef])
          state = STATE_RESERVED
        } else {
          curHead = aHead.get()
        }
      }
    }

    if (state == STATE_RESERVED) el.asInstanceOf[A]
    else default
  }

  def size(): Int = if (isEmpty()) 0 else 1
}
