package zio

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.mutable

private[zio] trait QueuePlatformSpecific {

  // java.util.concurrent.ConcurrentLinkedDeque is not available in Scala Native, so we need to createa custom `addFirst` method
  /**
   * The deque in which a `Queue` keeps the promises of the fibers suspended in
   * `take` and, for a back-pressured queue, the values of the suspended offers.
   * It is `java.util.concurrent.ConcurrentLinkedDeque` on the JVM,
   * `java.util.ArrayDeque` on Scala.js, and a `ConcurrentLinkedQueue` with an
   * added `addFirst` method on Scala Native.
   */
  private[zio] final class ConcurrentDeque[A <: AnyRef] extends ConcurrentLinkedQueue[A] {

    def addFirst(a: A): Unit = {
      var popped = poll()
      if (popped eq null) {
        offer(a)
      } else {
        val buf = new mutable.ArrayBuffer[A]
        while (popped ne null) {
          buf += popped
          popped = poll()
        }
        offer(a)
        buf.foreach(offer)
      }
    }

  }
}
