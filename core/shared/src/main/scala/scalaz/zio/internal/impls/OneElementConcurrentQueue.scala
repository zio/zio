package scalaz.zio.internal.impls

import scalaz.zio.internal.impls.padding.OneElementQueueFields
import scalaz.zio.internal.impls.padding.OneElementQueueFields.{ refUpdater, headUpdater, tailUpdater }

object OneElementConcurrentQueue {
  final val STATE_LOOP     = 0
  final val STATE_FULL     = 1
  final val STATE_EMPTY    = -1
  final val STATE_RESERVED = 2
}

class OneElementConcurrentQueue[A] extends OneElementQueueFields[A] {
  import OneElementConcurrentQueue._

  val capacity: Int = 1

  def dequeuedCount(): Long = headUpdater.get(this)
  def enqueuedCount(): Long = tailUpdater.get(this)

  def isEmpty(): Boolean = refUpdater.get(this) == null
  def isFull(): Boolean  = !isEmpty()

  def offer(a: A): Boolean = {
    assert(a != null)

    var state = STATE_LOOP
    val aTail = tailUpdater

    val aHead   = headUpdater
    var curHead = aHead.get(this)

    while (state == STATE_LOOP) {
      if (isFull() || curHead + 1 == aTail.get(this)) {
        state = STATE_FULL
      } else { // try to reserve the opportunity to offer
        if (aTail.compareAndSet(this, curHead, curHead + 1)) {
          refUpdater.lazySet(this, a.asInstanceOf[AnyRef])
          state = STATE_RESERVED
        } else {
          curHead = aHead.get(this)
        }
      }
    }

    if (state == STATE_RESERVED) true
    else false
  }

  def poll(default: A): A = {
    var state = STATE_LOOP
    var el    = null.asInstanceOf[AnyRef]

    val aHead   = headUpdater
    var curHead = aHead.get(this)

    val aTail = tailUpdater

    while (state == STATE_LOOP) {
      if (isEmpty() || aTail.get(this) == curHead) {
        state = STATE_EMPTY
      } else {
        if (aHead.compareAndSet(this, curHead, curHead + 1)) {
          el = refUpdater.get(this)
          refUpdater.lazySet(this, null.asInstanceOf[AnyRef])
          state = STATE_RESERVED
        } else {
          curHead = aHead.get(this)
        }
      }
    }

    if (state == STATE_RESERVED) el.asInstanceOf[A]
    else default
  }

  def size(): Int = if (isEmpty()) 0 else 1
}
