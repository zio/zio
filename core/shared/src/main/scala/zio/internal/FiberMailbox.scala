package zio.internal

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.ConcurrentLinkedQueue

final class FiberMailbox extends AtomicInteger(0) {
  import FiberMailbox._

  private val slot0 = new AtomicReference[AnyRef](null)
  private val slot1 = new AtomicReference[AnyRef](null)
  private val slot2 = new AtomicReference[AnyRef](null)
  private val slot3 = new AtomicReference[AnyRef](null)

  @volatile private var readIndex: Int = 0

  @volatile private var overflow: ConcurrentLinkedQueue[AnyRef] = null

  def add(message: AnyRef): Boolean = {
    val idx = getAndIncrement()
    idx match {
      case 0 => slot0.set(message)
      case 1 => slot1.set(message)
      case 2 => slot2.set(message)
      case 3 => slot3.set(message)
      case _ => ensureOverflow().offer(message)
    }
    true
  }

  def poll(): AnyRef = {
    val ri = readIndex
    if (ri < NUM_SLOTS) {
      val msg = getSlot(ri)
      if (msg != null) {
        clearSlot(ri)
        readIndex = ri + 1
        msg
      } else {
        val wi = get()
        if (wi <= ri) {
          null
        } else {
          spinForSlot(ri)
        }
      }
    } else {
      val ov = overflow
      if (ov != null) ov.poll() else null
    }
  }

  private def spinForSlot(ri: Int): AnyRef = {
    var spins       = 0
    var msg: AnyRef = null
    while ((msg eq null) && spins < MAX_SPINS) {
      msg = getSlot(ri)
      spins += 1
    }
    if (msg != null) {
      clearSlot(ri)
      readIndex = ri + 1
      msg
    } else {
      null
    }
  }

  def isEmpty: Boolean = {
    val ri = readIndex
    val wi = get()
    if (ri < NUM_SLOTS) {
      if (wi <= ri) {
        val ov = overflow
        ov == null || ov.isEmpty
      } else {
        false
      }
    } else {
      val ov = overflow
      ov == null || ov.isEmpty
    }
  }

  @inline private def getSlot(idx: Int): AnyRef =
    idx match {
      case 0 => slot0.get()
      case 1 => slot1.get()
      case 2 => slot2.get()
      case 3 => slot3.get()
      case _ => null
    }

  @inline private def clearSlot(idx: Int): Unit =
    idx match {
      case 0 => slot0.lazySet(null)
      case 1 => slot1.lazySet(null)
      case 2 => slot2.lazySet(null)
      case 3 => slot3.lazySet(null)
      case _ => ()
    }

  private def ensureOverflow(): ConcurrentLinkedQueue[AnyRef] = {
    var ov = overflow
    if (ov == null) {
      this.synchronized {
        ov = overflow
        if (ov == null) {
          ov = new ConcurrentLinkedQueue[AnyRef]()
          overflow = ov
        }
      }
    }
    ov
  }

  override def toString: String =
    s"FiberMailbox(writeIndex=${get()}, readIndex=$readIndex, isEmpty=$isEmpty)"
}

object FiberMailbox {
  final val NUM_SLOTS = 4
  final val MAX_SPINS = 64
}
