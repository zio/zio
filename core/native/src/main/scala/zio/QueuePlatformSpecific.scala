package zio

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Predicate

private[zio] trait QueuePlatformSpecific {
  // Scala Native provides ConcurrentLinkedDeque, but the raw implementation
  // regresses QueueSpec's bounded backpressure stress tests under contention.
  // Keep using the standard deque underneath, but serialize access through a
  // thin adapter for the Native queue bookkeeping paths.
  private[zio] final class ConcurrentDeque[A <: AnyRef] {
    private[this] val underlying = new ConcurrentLinkedDeque[A]()

    def addFirst(a: A): Unit =
      this.synchronized {
        underlying.addFirst(a)
      }

    def offer(a: A): Boolean =
      this.synchronized {
        underlying.offer(a)
      }

    def poll(): A =
      this.synchronized {
        underlying.poll()
      }

    def isEmpty(): Boolean =
      this.synchronized {
        underlying.isEmpty()
      }

    def size(): Int =
      this.synchronized {
        underlying.size()
      }

    def removeIf(filter: Predicate[_ >: A]): Boolean =
      this.synchronized {
        underlying.removeIf(filter)
      }

    def remove(a: A): Boolean =
      this.synchronized {
        underlying.remove(a)
      }
  }
}
