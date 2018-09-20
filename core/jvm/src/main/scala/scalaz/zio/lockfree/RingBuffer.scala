package scalaz.zio.lockfree

import java.util.concurrent.atomic.{ AtomicLong, AtomicReferenceArray }

class RingBuffer[A](val desiredCapacity: Int) extends LFQueueWithPaddedFields[A](desiredCapacity) {
  private val idxMask: Long = (capacity - 1).toLong

  0.until(capacity).foreach(i => buf.set(i, BufferElement.buildEmpty(i.toLong)))

  override def size(): Int = (tail.get() - head.get()).toInt

  override def enqueuedCount(): Long = tail.get()

  override def dequeuedCount(): Long = head.get()

  override def offer(a: A): Boolean = {
    val aCapacity = capacity
    val aMask     = idxMask
    val aBuf      = buf

    var curPosition = tail.get()
    var curHead     = head.get()

    while (curPosition < curHead + aCapacity) {
      val idx = posToIdx(curPosition, aMask)
      val el  = aBuf.get(idx)

      if (el.isEmpty) {
        if (el.position == curPosition) {
          if (aBuf.compareAndSet(idx, el, BufferElement.buildValue(curPosition, a))) {
            val curTail = tail.get()
            if (curTail != curPosition) updateTail(tail, curPosition + 1) // tail.compareAndSet(curTail, curPosition + 1)
            return true
          } else {
            curPosition += 1
          }
        } else if (el.position > curPosition) {
          curPosition = Math.max(tail.get(), curHead)
        } else {
          sys.error(s"Offer error: impossible case $el")
        }
      } else { // notEmpty
        curPosition += 1
      }

      curHead = head.get()
    }

    false
  }

  override def poll(): Option[A] = {
    val aHead       = head
    var curPosition = aHead.get()
    val aCapacity   = capacity
    val aMask       = idxMask
    val aBuf        = buf
    var spin        = true

    while (spin) {
      val nextEmptyPosition = curPosition + aCapacity

      val idx = posToIdx(curPosition, aMask)
      val el  = aBuf.get(idx)

      if (!el.isEmpty) { // there's a value
        if (el.position == curPosition) {
          if (aBuf.compareAndSet(idx, el, BufferElement.buildEmpty(nextEmptyPosition))) {
            updateHead(aHead, curPosition + 1)
            return Some(el.getValue[A])
          } else {
            curPosition += 1
          }
        } else if (el.position == nextEmptyPosition) {
          curPosition += 1
        } else if (el.position > nextEmptyPosition) {
          curPosition = aHead.get()
        } else if (el.position > curPosition) { // we hit the tail
          spin = false
//          val curTail = tail.get()
//          if (curTail < curPosition) updateTail(tail, curPosition) //tail.compareAndSet(curTail, curPosition)
        } else {
          sys.error(s"Poll error 1: impossible case ${el.position} - $curPosition")
        }
      } else { // empty
        if (el.position == nextEmptyPosition) {
          curPosition += 1
        } else if (el.position > nextEmptyPosition) {
          curPosition = aHead.get()
        } else if (el.position >= curPosition) { // we hit the tail
          spin = false
//          val curTail = tail.get()
//          if (curTail < curPosition) updateTail(tail, curPosition) //tail.compareAndSet(curTail, curPosition)
        } else {
          sys.error(s"Poll error 2: impossible case ${el.position} - $curPosition")
        }
      }
    }

    None
  }

  override def isEmpty(): Boolean = tail.get() == head.get()

  override def isFull(): Boolean = tail.get() == head.get() + capacity - 1

  @inline
  private def posToIdx(pos: Long, mask: Long): Int = (pos & mask).toInt

  @inline
  private def updateTail(aTail: AtomicLong, pos: Long): Unit = {
    var curTail = aTail.get()

    while (curTail < pos) {
      if (!aTail.compareAndSet(curTail, pos)) curTail = aTail.get()
    }
  }

  @inline
  private def updateHead(aHead: AtomicLong, pos: Long): Unit = {
    var curHead = aHead.get()

    while (curHead < pos) {
      if (aHead.compareAndSet(curHead, pos)) return else curHead = aHead.get()
    }
  }
}

/*
 */
private class BufferElement(val position: Long, val isEmpty: Boolean, val value: Object) {
  def getValue[A]: A = value.asInstanceOf[A]
}

private object BufferElement {
  def buildValue[A](position: Long, value: A): BufferElement =
    new BufferElement(position, false, value.asInstanceOf[Object])

  def buildEmpty(position: Long): BufferElement = new BufferElement(position, true, null)
}

/*
The classes below provide padding for contended fields in the RingBuffer layout, specifically `head`, `tail`, and `buf`.
 */
private[lockfree] abstract class PadL1 {
  protected val p001: Long = 0
  protected val p002: Long = 0
  protected val p003: Long = 0
  protected val p004: Long = 0
  protected val p005: Long = 0
  protected val p006: Long = 0
  protected val p007: Long = 0
  protected val p008: Long = 0
  protected val p009: Long = 0
  protected val p010: Long = 0
  protected val p011: Long = 0
  protected val p012: Long = 0
  protected val p013: Long = 0
  protected val p014: Long = 0
}

private[lockfree] abstract class PaddedHead extends PadL1 {
  protected val head: AtomicLong = new AtomicLong(0L)
}

private[lockfree] abstract class PadL2 extends PaddedHead {
  protected val p101: Long = 0
  protected val p102: Long = 0
  protected val p103: Long = 0
  protected val p104: Long = 0
  protected val p105: Long = 0
  protected val p106: Long = 0
  protected val p107: Long = 0
  protected val p108: Long = 0
  protected val p109: Long = 0
  protected val p110: Long = 0
  protected val p111: Long = 0
  protected val p112: Long = 0
  protected val p113: Long = 0
  protected val p114: Long = 0
  protected val p115: Long = 0
}

private[lockfree] abstract class PaddedHeadAndTail extends PadL2 {
  protected val tail: AtomicLong = new AtomicLong(0L)
}

private[lockfree] abstract class PadL3 extends PaddedHeadAndTail {
  protected val p201: Long = 0
  protected val p202: Long = 0
  protected val p203: Long = 0
  protected val p204: Long = 0
  protected val p205: Long = 0
  protected val p206: Long = 0
  protected val p207: Long = 0
  protected val p208: Long = 0
  protected val p209: Long = 0
  protected val p210: Long = 0
  protected val p211: Long = 0
  protected val p212: Long = 0
  protected val p213: Long = 0
  protected val p214: Long = 0
  protected val p215: Long = 0
}

private[lockfree] abstract class LFQueueWithPaddedFields[A](desiredCapacity: Int) extends PadL3 with LockFreeQueue[A] {
  override final val capacity: Int                       = nextPow2(desiredCapacity)
  protected val buf: AtomicReferenceArray[BufferElement] = new AtomicReferenceArray[BufferElement](capacity)
}
