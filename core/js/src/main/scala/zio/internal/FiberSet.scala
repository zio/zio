package zio.internal

import zio.internal.FiberSet.IsAlive
import zio.Duration

import java.util.HashSet

private[zio] final class FiberSet[A <: AnyRef](
  initialCapacity: Int,
  isAlive: IsAlive[A],
  autoGcEvery: Option[Duration] // accepted, silently ignored — no thread support on JS
) extends FiberSetPlatformSpecific[A](initialCapacity, isAlive, autoGcEvery) {
  private[this] val store = new HashSet[A](initialCapacity)

  def add(a: A): Unit          = { val _ = store.add(a) }
  def remove(a: A): Unit       = { val _ = store.remove(a) }
  def isEmpty: Boolean         = store.isEmpty
  def size: Int                = store.size()
  def gc(force: Boolean): Unit = ()

  def iterator: Iterator[A] = {
    val it = store.iterator()

    new Iterator[A] {
      private[this] var _next: A = prefetch()

      @scala.annotation.tailrec
      private[this] def prefetch(): A =
        if (it.hasNext) {
          val value = it.next()
          if (isAlive(value)) value else prefetch()
        } else {
          null.asInstanceOf[A]
        }

      def hasNext: Boolean = _next ne null

      def next(): A =
        if (_next eq null)
          throw new NoSuchElementException("There is no more element in the FiberSet iterator")
        else {
          val result = _next
          _next = prefetch()
          result
        }
    }
  }

  def withAutoGc(every: Duration): FiberSet[A] = this
}

private[zio] object FiberSet {

  /** Specialized Function1 that doesn't cause boxing of the Boolean */
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }

  object IsAlive {
    val always: IsAlive[Any] = _ => true
  }
}
