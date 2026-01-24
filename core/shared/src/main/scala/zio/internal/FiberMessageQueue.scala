package zio.internal

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference, AtomicReferenceArray}

private[zio] final class FiberMessageQueue extends java.io.Serializable {
  import FiberMessageQueue._

  private val head = new AtomicReference[Node](new Node)
  private val tail = new AtomicReference[Node](head.get)

  def add(item: FiberMessage): Boolean = {
    while (true) {
      val t = tail.get
      val idx = t.writeIdx.getAndIncrement()

      if (idx < Capacity) {
        t.items.set(idx, item)
        return true
      } else {
        // This node is full, try to move to next
        var next = t.next.get
        if (next == null) {
          val n = new Node
          n.items.set(0, item)
          n.writeIdx.set(1)
          if (t.next.compareAndSet(null, n)) {
             tail.compareAndSet(t, n)
             return true
          }
          next = t.next.get
        }
        if (next != null) {
          tail.compareAndSet(t, next)
        }
      }
    }
    false
  }

  def poll(): FiberMessage = {
    val h = head.get
    val idx = h.readIdx

    if (idx < Capacity) {
      var item = h.items.getAndSet(idx, null).asInstanceOf[FiberMessage]

      if (item != null) {
        h.readIdx = idx + 1
        return item
      } else {
        // Slot is null.
        // Check if a writer has claimed this slot
        if (h.writeIdx.get() > idx) {
          // Writer is writing... spin wait
          while ({ item = h.items.getAndSet(idx, null).asInstanceOf[FiberMessage]; item == null }) {
            java.lang.Thread.onSpinWait()
          }
          h.readIdx = idx + 1
          return item
        }
        // Truly empty
        return null
      }
    } else {
      // Node consumed. Check next.
      val n = h.next.get
      if (n != null) {
        head.set(n)
        poll()
      } else {
        null
      }
    }
  }

  def isEmpty: Boolean = {
    val h = head.get
    val idx = h.readIdx
    if (idx < Capacity) {
      if (h.items.get(idx) != null) return false
      if (h.writeIdx.get() > idx) return false
    } else {
      if (h.next.get != null) return false
    }
    true
  }
}

private[zio] object FiberMessageQueue {
  final val Capacity = 4

  final class Node {
    val items = new AtomicReferenceArray[AnyRef](Capacity)
    val writeIdx = new AtomicInteger(0)
    var readIdx = 0
    val next = new AtomicReference[Node]()
  }
}
