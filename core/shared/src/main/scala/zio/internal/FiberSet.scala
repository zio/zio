package zio.internal

import zio.Fiber
import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

private[zio] trait FiberSet {
  def add(f: Fiber.Runtime[_, _]): Unit
  def remove(f: Fiber.Runtime[_, _]): Unit
  def foreach(g: Fiber.Runtime[_, _] => Unit): Unit
  def sizeApprox: Int
}

private[zio] object FiberSet {
  def make(capacityHint: Int = 64): FiberSet = new Impl(capacityHint)

  private val Dummy: AnyRef = new AnyRef

  /** A weak ref whose hashCode never changes (cached identity hash).
    * equals is reference-equality on the box (so we can remove polled keys directly).
    */
  private final class WeakBox[A <: AnyRef](a: A, q: ReferenceQueue[A])
      extends WeakReference[A](a, q) {
    private[this] val hc = System.identityHashCode(a)
    override def hashCode(): Int = hc
    override def equals(other: Any): Boolean = this.asInstanceOf[AnyRef] eq other.asInstanceOf[AnyRef]
  }

  private final class Impl(capacityHint: Int) extends FiberSet {
    private[this] val queue = new ReferenceQueue[Fiber.Runtime[_, _]]()
    private[this] val map   = new ConcurrentHashMap[WeakBox[Fiber.Runtime[_, _]], AnyRef](capacityHint)

    // micro-throttle draining the reference queue
    @volatile private[this] var opsSinceDrain = 0
    private[this] val DrainEvery              = 64

    private def drainQueue(): Unit = {
      var polled = queue.poll()
      while (polled ne null) {
        map.remove(polled.asInstanceOf[WeakBox[Fiber.Runtime[_, _]]])
        polled = queue.poll()
      }
    }

    override def add(f: Fiber.Runtime[_, _]): Unit = {
      map.put(new WeakBox(f, queue), Dummy)
      val n = opsSinceDrain + 1
      opsSinceDrain = if (n >= DrainEvery) { drainQueue(); 0 } else n
    }

    override def remove(f: Fiber.Runtime[_, _]): Unit = {
      // best-effort scan (ticket does not require strict set semantics)
      val it = map.keySet().iterator()
      while (it.hasNext) {
        val k = it.next()
        val r = k.get()
        if ((r eq null) || (r eq f)) {
          it.remove()
          if (r eq f) return
        }
      }
      val n = opsSinceDrain + 1
      opsSinceDrain = if (n >= DrainEvery) { drainQueue(); 0 } else n
    }

    override def foreach(g: Fiber.Runtime[_, _] => Unit): Unit = {
      drainQueue()
      val it = map.keySet().iterator()
      while (it.hasNext) {
        val ref = it.next()
        val r   = ref.get()
        if (r eq null) it.remove()
        else g(r)
      }
    }

    override def sizeApprox: Int = { drainQueue(); map.size() }
  }
}
