package zio.internal

import zio.Duration
import zio.internal.FiberSet.IsAlive

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import scala.annotation.tailrec

/**
 * A weakly-held, concurrently mutable collection optimized for tracking live
 * fibers. Duplicates are tolerated, but remove and iteration both filter out
 * fibers that are no longer alive.
 */
private[zio] final class FiberSet[A <: AnyRef] private (isAlive: IsAlive[A]) { self =>
  import FiberSet.Ref

  private[this] val queue    = new ReferenceQueue[A]
  private[this] val refs     = new ConcurrentLinkedQueue[Ref[A]]
  private[this] val gcStatus = new AtomicBoolean(false)
  private[this] val autoGc   = new AtomicBoolean(false)
  private[this] val inactiveRef = new Predicate[Ref[A]] {
    def test(ref: Ref[A]): Boolean = {
      val fiber = ref.get()
      !ref.active.get() || (fiber eq null) || !isAlive(fiber)
    }
  }

  def withAutoGc(every: Duration): FiberSet[A] = {
    if (autoGc.compareAndSet(false, true)) {
      FiberSetGc.start(self, every)
    }
    self
  }

  final def add(fiber: A): Unit =
    if ((fiber ne null) && isAlive(fiber)) {
      refs.offer(new Ref[A](fiber, queue))
      ()
    }

  final def remove(fiber: A): Unit =
    if (fiber ne null) {
      val iterator = refs.iterator()
      while (iterator.hasNext) {
        val ref   = iterator.next()
        val value = ref.get()
        if ((value eq null) || !isAlive(value)) ref.active.set(false)
        else if (value eq fiber) ref.active.set(false)
      }
    }

  final def gc(): Unit =
    gc(true)

  final def gc(force: Boolean): Unit = {
    val lockAcquired = gcStatus.compareAndSet(false, true)

    try
      if (force || lockAcquired) {
        drainQueue()
        refs.removeIf(inactiveRef)
      }
    finally if (lockAcquired) gcStatus.set(false)
  }

  final def isEmpty: Boolean =
    !iterator.hasNext

  final def iterator: Iterator[A] =
    new Iterator[A] {
      private[this] val refsIterator = {
        gc(false)
        refs.iterator()
      }
      private[this] var nextValue = prefetch()

      @tailrec
      private def prefetch(): A =
        if (refsIterator.hasNext) {
          val ref   = refsIterator.next()
          val value = ref.get()
          if (ref.active.get() && (value ne null) && isAlive(value)) value
          else {
            ref.active.set(false)
            prefetch()
          }
        } else null.asInstanceOf[A]

      def hasNext: Boolean =
        nextValue ne null

      def next(): A =
        if (nextValue eq null)
          throw new NoSuchElementException("There is no more element in the fiber set iterator")
        else {
          val result = nextValue
          nextValue = prefetch()
          result
        }
    }

  final def size: Int =
    iterator.length

  override final def toString(): String =
    iterator.mkString("FiberSet(", ",", ")")

  private def drainQueue(): Unit = {
    var ref = queue.poll().asInstanceOf[Ref[A]]
    while (ref ne null) {
      ref.active.set(false)
      ref = queue.poll().asInstanceOf[Ref[A]]
    }
  }
}

private[zio] object FiberSet {

  def apply[A <: AnyRef](isAlive: IsAlive[A] = IsAlive.always): FiberSet[A] =
    new FiberSet[A](isAlive)

  /** Specialized Function1 that doesn't cause boxing of the Boolean. */
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }

  object IsAlive {
    val always: IsAlive[AnyRef] = _ => true
  }

  private final class Ref[A <: AnyRef](
    fiber: A,
    queue: ReferenceQueue[A]
  ) extends WeakReference[A](fiber, queue) {
    val active = new AtomicBoolean(true)
  }
}
