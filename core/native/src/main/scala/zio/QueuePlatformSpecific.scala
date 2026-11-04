package zio

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Predicate

private[zio] trait QueuePlatformSpecific {

  // Scala Native's `ConcurrentLinkedDeque` has a loop-termination bug in `linkFirst`, `linkLast`,
  // `first`, and `last` under concurrent contention (`restart = true` inside `while (true)`).
  // Synchronizing on the underlying `ConcurrentLinkedDeque` provides O(1) `addFirst` while avoiding the spin.
  private[zio] final class ConcurrentDeque[A <: AnyRef] {
    private[this] val underlying = new ConcurrentLinkedDeque[A]()

    def addFirst(a: A): Unit =
      this.synchronized(underlying.addFirst(a))

    def isEmpty(): Boolean =
      this.synchronized(underlying.isEmpty())

    def offer(a: A): Boolean =
      this.synchronized(underlying.offer(a))

    def poll(): A =
      this.synchronized(underlying.poll())

    def remove(a: A): Boolean =
      this.synchronized(underlying.remove(a))

    def removeIf(filter: Predicate[_ >: A]): Boolean =
      this.synchronized(underlying.removeIf(filter))

    def size(): Int =
      this.synchronized(underlying.size())
  }
}
