package zio

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Predicate

private[zio] trait QueuePlatformSpecific {

  // Scala Native supports ConcurrentLinkedDeque (since 0.5.6, via
  // https://github.com/scala-native/scala-native/pull/4046).
  // We wrap it with a synchronized adapter so that stress-test behaviour
  // matches the JVM: the raw Scala Native ConcurrentLinkedDeque regresses
  // the back-pressured bounded-queue stress tests without the adapter.
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
