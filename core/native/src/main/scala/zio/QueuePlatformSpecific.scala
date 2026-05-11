package zio

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Predicate

private[zio] trait QueuePlatformSpecific {
  private[zio] final class ConcurrentDeque[A <: AnyRef] {
    private[this] val deque = new ConcurrentLinkedDeque[A]

    def addFirst(a: A): Unit =
      deque.synchronized(deque.addFirst(a))

    def isEmpty(): Boolean =
      deque.synchronized(deque.isEmpty())

    def offer(a: A): Boolean =
      deque.synchronized(deque.offer(a))

    def poll(): A =
      deque.synchronized(deque.poll())

    def remove(a: Any): Boolean =
      deque.synchronized(deque.remove(a))

    def removeIf(filter: Predicate[_ >: A]): Boolean =
      deque.synchronized(deque.removeIf(filter))

    def size(): Int =
      deque.synchronized(deque.size())
  }
}
