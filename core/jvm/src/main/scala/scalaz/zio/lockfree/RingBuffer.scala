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
    val aHead     = head
    val aTail     = tail

    var curPosition = aHead.get()
    var curHead     = aTail.get()

    while (curPosition < curHead + aCapacity) {
      val idx = posToIdx(curPosition, aMask)
      val el  = aBuf.get(idx)

      if (el.isEmpty) {
        if (el.position == curPosition) {
          if (aBuf.compareAndSet(idx, el, BufferElement.buildValue(curPosition, a))) {
            val curTail = aTail.get()
            if (curTail != curPosition) updateTail(aTail, curPosition + 1) // tail.compareAndSet(curTail, curPosition + 1)
            return true
          } else {
            curPosition += 1
          }
        } else if (el.position > curPosition) {
          curPosition = Math.max(aTail.get(), curHead)
        } else {
          sys.error(s"Offer error: impossible case $el")
        }
      } else { // notEmpty
        curPosition += 1
      }

      curHead = aHead.get()
    }

    false
  }

  override def poll(): Option[A] = {
    val aCapacity = capacity
    val aMask     = idxMask
    val aBuf      = buf
    val aHead     = head

    var curPosition = aHead.get()
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
 * The classes below provide padding for contended fields in the RingBuffer layout,
 * specifically `head`, `tail`, and `buf`.
 *
 * Unlike C, we don't have control over the layout of the object in memory.
 * The layout is dynamic and is under control of the JVM which can shuffle
 * fields to optimize the object's footprint. However, when the object is used
 * in a concurrent setting having all fields densely packed may affect performance
 * since certain concurrently modified fields can fall on the same cache-line and
 * be subject to _False Sharing_. In such case we would like to space out _hot_ fields
 * and thus make access more cache-friendly.
 *
 * On x86 one cache-line is 64KiB and we'd need to space out fields by at least *2*
 * cache-lines because modern processors do pre-fetching, i.e. get the requested
 * cache-line and the one after. Thus we need ~128KiB of space in-between hot fields.
 *
 * One solution could be just adding a bunch of Longs in-between hot fields, but if
 * those are defined within a single class JVM will likely shuffle the longs and
 * mess up the padding.
 *
 * There is a workaround that relies on the fact the fields of a super-class go
 * before fields of a sub-class in the object's layout. So, a properly crafted
 * inheritance chain can guarantee proper spacing of the hot fields.
 *
 * This is exactly what's happening below.
 *
 * **IMPORTANT** Current solution should work on x86-64, but may not work 100% as
 * expected on other architectures. A proper solution would be to use something like
 * [[@Contended]] annotation, but the problem is this is under [[sun.misc]] on Java 8,
 * although OpenJDK moved it under [[jdk.internal.vm.annotation]] in later versions of
 * Java.
 *
 * To illustrate the effect of such padding, here's an output for [[RingBuffer]]
 * by JOL (truncated):
 * scalaz.zio.lockfree.RingBuffer object internals:
 * OFFSET  SIZE          TYPE DESCRIPTION
 *     0     4                 (object header)
 *     4     4                 (object header)
 *     8     4                 (object header)
 *    12     4                 int PadL1.p000
 *    16     8                 long PadL1.p001
 *    24     8                 long PadL1.p002
 *   ...
 *   120     8                 long PadL1.p014
 * >>128     4    atomic.AtomicLong PaddedHead.head
 *   132     4                  int PadL2.p100
 *   136     8                 long PadL2.p101
 *   ...
 *   248     8                 long PadL2.p115
 * >>256     4    atomic.AtomicLong PaddedHeadAndTail.tail
 *   260     4                  int PadL3.p200
 *   264     8                 long PadL3.p201
 *   ...
 *   376     8                 long PadL3.p215
 *   384     4                  int PaddedHeadTailAndBuf.capacity
 * >>388     4 AtomicReferenceArray PaddedHeadTailAndBuf.buf
 *   392     8                 long LFQueueWithPaddedFields.p301
 *   400     8                 long LFQueueWithPaddedFields.p302
 *   ...
 *   496     4                  int LFQueueWithPaddedFields.p314
 *   500     4                  int RingBuffer.desiredCapacity
 *   504     8                 long RingBuffer.idxMask
 * Instance size: 512 bytes
 */
private[lockfree] abstract class PadL1 {
  protected val p000: Int  = 0
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
  protected val p100: Int  = 0
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
  protected val p200: Int  = 0
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

private[lockfree] abstract class PaddedHeadTailAndBuf[A](desiredCapacity: Int) extends PadL3 with LockFreeQueue[A] {
  override final val capacity: Int                       = nextPow2(desiredCapacity)
  protected val buf: AtomicReferenceArray[BufferElement] = new AtomicReferenceArray[BufferElement](capacity)
}

private[lockfree] abstract class LFQueueWithPaddedFields[A](desiredCapacity: Int)
    extends PaddedHeadTailAndBuf[A](desiredCapacity) {
  protected val p301: Long = 0
  protected val p302: Long = 0
  protected val p303: Long = 0
  protected val p304: Long = 0
  protected val p305: Long = 0
  protected val p306: Long = 0
  protected val p307: Long = 0
  protected val p308: Long = 0
  protected val p309: Long = 0
  protected val p310: Long = 0
  protected val p311: Long = 0
  protected val p312: Long = 0
  protected val p313: Long = 0
  protected val p314: Int  = 0
}
