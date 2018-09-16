package scalaz.zio.lockfree

import java.util.concurrent.atomic.{ AtomicLong, AtomicReferenceArray }

class BufferElement(val position: Long, val isEmpty: Boolean, val value: Object) {
  def getValue[A]: A = value.asInstanceOf[A]
}

object BufferElement {
  def buildValue[A](position: Long, value: A): BufferElement =
    new BufferElement(position, false, value.asInstanceOf[Object])

  def buildEmpty(position: Long): BufferElement = new BufferElement(position, true, null)
}

class RingBuffer[A](val desiredCapacity: Int) extends LockFreeQueue[A] {
  override final val capacity: Int = nextPow2(desiredCapacity)
  private val idxMask: Long        = (capacity - 1).toLong

  private val head: AtomicLong = new AtomicLong(0L)

  private val tail: AtomicLong = new AtomicLong(0L)

  private val buf: AtomicReferenceArray[BufferElement] = new AtomicReferenceArray[BufferElement](capacity)

  0.until(capacity).foreach(i => buf.set(i, BufferElement.buildEmpty(i.toLong)))

  override def relaxedSize(): Int = (tail.get() - head.get()).toInt

  override def enqueuedCount(): Long = tail.get()

  override def dequeuedCount(): Long = head.get()

  override def offer(a: A): Boolean = {
    var curPosition = tail.get()
    var curHead     = head.get()
    val aCapacity   = capacity
    val aMask       = idxMask
    val aBuf        = buf
    val lag         = 2

    while (curPosition < curHead + aCapacity) {
      val idx = posToIdx(curPosition, aMask)
      val el  = aBuf.get(idx)

      if (el.isEmpty) {
        if (el.position == curPosition) {
          if (aBuf.compareAndSet(idx, el, BufferElement.buildValue(curPosition, a))) {
            val curTail = tail.get()
            if (curTail <= curPosition - lag) tail.compareAndSet(curTail, curPosition + 1)
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
          val curTail = tail.get()
          if (curTail < curPosition) tail.compareAndSet(curTail, curPosition)
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
          val curTail = tail.get()
          if (curTail < curPosition) tail.compareAndSet(curTail, curPosition)
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

//  private def updateTail(pos: Long): Unit = {
//    var curTail = tail.get()
//
//    while (curTail < pos) {
//      if (!tail.compareAndSet(curTail, pos)) curTail = tail.get()
//    }
//  }

  private def updateHead(aHead: AtomicLong, pos: Long): Unit = {
    var curHead = aHead.get()

    while (curHead < pos) {
      if (aHead.compareAndSet(curHead, pos)) return else curHead = aHead.get()
    }
  }
}
