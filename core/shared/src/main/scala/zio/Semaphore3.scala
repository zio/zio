package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.annotation.tailrec

sealed trait Semaphore3 extends Serializable {

  def available(implicit trace: Trace): UIO[Long]

  def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  def release(n: Long)(implicit trace: Trace): UIO[Unit]
}

object Semaphore3 {

  def make(permits: Long)(implicit trace: Trace): UIO[Semaphore3] =
    ZIO.succeed(new ConcurrentSemaphore(permits))

  private final class ConcurrentSemaphore(initial: Long) extends Semaphore3 {
    private[this] val permits = new AtomicLong(initial)
    private[this] val waiters = new WaiterQueue

    override def available(implicit trace: Trace): UIO[Long] = ZIO.succeed(permits.get())

    override def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    override def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.uninterruptibleMask { restore =>
        restore(acquireN(n)) *> zio.ensuring(releaseN(n))
      }

    override def release(n: Long)(implicit trace: Trace): UIO[Unit] = releaseN(n)

    private def acquireN(n: Long)(implicit trace: Trace): UIO[Unit] =
      ZIO.asyncInterrupt[Any, Nothing, Unit] { cb =>
        if (tryAcquireN(n)) {
          Right(ZIO.unit)
        } else {
          val waiter = new Waiter(n, cb)
          waiters.enqueue(waiter)
          // `tryAcquireN` after enqueueing to handle the case where a permit is released
          // between the initial `tryAcquireN` and enqueueing the waiter.
          if (tryAcquireN(n) && waiters.tryDequeue(waiter)) {
            Right(ZIO.unit)
          } else {
            Left(ZIO.succeed(waiters.tryDequeue(waiter)))
          }
        }
      }

    private def releaseN(n: Long)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed {
        permits.addAndGet(n)
        var available = permits.get()
        var continue  = true
        while (continue && available > 0 && waiters.nonEmpty) {
          waiters.head match {
            case Some(waiter) if waiter.permits <= available =>
              // Remove the waiter from the queue
              waiters.dequeue()
              // Complete the waiter's callback to unblock it
              waiter.cb(ZIO.unit)
              // Update available permits after serving (the waiter will acquire the permits)
              available = permits.addAndGet(-waiter.permits)
            case _ =>
              // Can't serve head waiter, stop processing
              continue = false
          }
        }
      }

    private def tryAcquireN(n: Long): Boolean = {
      @tailrec
      def loop(): Boolean = {
        val current = permits.get()
        if (current >= n) {
          if (permits.compareAndSet(current, current - n)) true
          else loop()
        } else {
          false
        }
      }
      loop()
    }
  }

  private final class Waiter(val permits: Long, val cb: UIO[Unit] => Unit)

  private final class WaiterQueue {
    private[this] val headRef = new AtomicReference[Node](null)
    private[this] val tailRef = new AtomicReference[Node](null)

    def enqueue(waiter: Waiter): Unit = {
      val newNode = new Node(waiter)
      @tailrec
      def loop(): Unit = {
        val currentTail = tailRef.get()
        if (currentTail == null) {
          if (headRef.compareAndSet(null, newNode)) {
            tailRef.set(newNode)
          } else {
            loop()
          }
        } else {
          if (currentTail.next.compareAndSet(null, newNode)) {
            tailRef.compareAndSet(currentTail, newNode)
          } else {
            loop()
          }
        }
      }
      loop()
    }

    def dequeue(): Option[Waiter] = {
      @tailrec
      def loop(): Option[Waiter] = {
        val currentHead = headRef.get()
        if (currentHead == null) {
          None
        } else {
          val next = currentHead.next.get()
          if (headRef.compareAndSet(currentHead, next)) {
            // If we're removing the last node, update tailRef
            if (next == null) {
              tailRef.compareAndSet(currentHead, null)
            }
            Some(currentHead.waiter)
          } else {
            loop()
          }
        }
      }
      loop()
    }

    // Add head method to peek at the first waiter without dequeuing
    def head: Option[Waiter] =
      Option(headRef.get()).map(_.waiter)

    def tryDequeue(waiter: Waiter): Boolean = {
      @tailrec
      def loop(prev: Node, curr: Node): Boolean =
        if (curr == null) {
          false
        } else {
          if (curr.waiter == waiter) {
            prev.next.compareAndSet(curr, curr.next.get())
          } else {
            loop(curr, curr.next.get())
          }
        }

      val h = headRef.get()
      if (h == null) {
        false
      } else if (h.waiter == waiter) {
        headRef.compareAndSet(h, h.next.get())
      } else {
        loop(h, h.next.get())
      }
    }

    def nonEmpty: Boolean = headRef.get() != null

  }
  private class Node(val waiter: Waiter) {
    val next = new AtomicReference[Node](null)
  }
}
