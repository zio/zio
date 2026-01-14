package zio.internal.impls

import zio.Semaphore.SemaphoreBase
import zio.{Exit, Scope, Trace, UIO, ZIO}

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.annotation.tailrec

private[zio] object SemaphoreImpls {
  private val rightExitUnit = Right(Exit.unit)

  final class ConcurrentSemaphore(
    val initialPermits: Long,
    val fair: Boolean
  ) extends SemaphoreBase {

    private val waiterQueue = WaiterQueue(initialPermits)

    def available(implicit trace: Trace): UIO[Long] = ZIO.succeed(waiterQueue.getVolatilePermits)

    override def awaiting(implicit trace: Trace): UIO[Long] = ZIO.succeed(waiterQueue.waiterSize())

    def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.acquireReleaseWith(reserve(n)) { reservation =>
        release(reservation)
      } { reservation =>
        await(reservation) *> zio
      }

    override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
      ZIO.acquireReleaseWith(tryReserve(n)) {
        case Some(reservation) => release(reservation)
        case _                 => Exit.unit
      } {
        case Some(reservation) => await(reservation) *> zio.map(Some(_))
        case _                 => ZIO.none
      }

    def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      ZIO
        .acquireRelease(reserve(n)) { reservation =>
          release(reservation)
        }
        .flatMap { reservation =>
          await(reservation)
        }

    private def release(reservation: Reservation)(implicit trace: Trace): UIO[Unit] = ZIO.succeed {
      reservation match {
        case ZeroReservation            => ()
        case FastReservation(requested) =>
          // We permitted some requests, return them back
          waiterQueue.getAndAddPermits(requested)
          pollLoop()
        case WaitReservation(waiter) =>
          waiter.getAndSet(Done) match {
            case Permitted =>
              // We permitted some requests, return them back
              waiterQueue.getAndAddPermits(waiter.requested)
              pollLoop()
            case _ => ()
          }
      }
    }

    private def await(reservation: Reservation)(implicit trace: Trace): UIO[Unit] = ZIO.suspendSucceed {
      reservation match {
        case ZeroReservation    => Exit.unit
        case FastReservation(_) => Exit.unit
        case WaitReservation(waiter) =>
          waiter.get() match {
            case Uninitialized =>
              ZIO.fiberIdWith { fiberId =>
                ZIO.Async[Any, Nothing, Unit](
                  trace = trace,
                  registerCallback = cb => {
                    if (waiter.compareAndSet(Uninitialized, Waiting(cb))) {
                      null
                    } else {
                      rightExitUnit
                    }
                  },
                  blockingOn = () => fiberId
                )
              }
            case _ => Exit.unit
          }
      }
    }

    private def pollLoop(): Unit = {

      @tailrec
      def permitWaiter(waiter: Waiter, acc: List[WaiterCallback]): List[WaiterCallback] =
        waiter.get() match {
          case Done =>
            // This is done before we grant the permission, so we need to return what we've taken from the queue
            waiterQueue.getAndAddPermits(waiter.requested)
            acc
          case other =>
            if (waiter.compareAndSet(other, Permitted)) {
              other match {
                case Waiting(cb) => cb :: acc // Add the callback to be executed later
                case _           => acc
              }
            } else {
              // retry
              permitWaiter(waiter, acc)
            }
        }

      @tailrec
      def pollWaiterLoop(acc: List[WaiterCallback]): List[WaiterCallback] = {
        val waiter = waiterQueue.poll()
        if (waiter ne null) {
          val nextAcc = permitWaiter(waiter, acc)
          pollWaiterLoop(nextAcc)
        } else {
          acc
        }
      }

      pollWaiterLoop(Nil).foreach(_.apply(Exit.unit))
    }

    @tailrec
    private def fastPath(permits: Long): Boolean = {
      val currentPermits = waiterQueue.getVolatilePermits
      val nextPermits    = currentPermits - permits
      if (nextPermits >= 0) {
        if (waiterQueue.compareAndSetPermits(currentPermits, nextPermits)) {
          true
        } else {
          fastPath(permits)
        }
      } else {
        false
      }
    }

    private def reserve(n: Long)(implicit trace: Trace): UIO[Reservation] = {
      def waitReserve(): Reservation = {
        val waiter = new Waiter(n)
        waiterQueue.offer(waiter)
        if (waiterQueue.peek() eq waiter) pollLoop()
        WaitReservation(waiter)
      }

      if (n < 0)
        ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
      else if (n == 0L)
        unitReservation
      else {
        ZIO.succeed {
          if (!fair || (waiterQueue.peek() eq null)) {
            // unfair scenario, we can try fast path
            if (fastPath(n)) {
              // successfully attemp the fast path, return
              FastReservation(n)
            } else {
              // do slow path
              waitReserve()
            }
          } else {
            // can't do fast path, do slow path
            waitReserve()
          }
        }
      }
    }

    private def tryReserve(n: Long)(implicit trace: Trace): UIO[Option[Reservation]] =
      if (n < 0)
        ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
      else if (n == 0L)
        Exit.succeed(Some(ZeroReservation))
      else {
        ZIO.succeed {
          if (fastPath(n)) {
            Some(FastReservation(n))
          } else {
            None
          }
        }
      }
  }

  private type WaiterCallback = UIO[Unit] => Unit

  private sealed trait WaiterState
  private case object Uninitialized                    extends WaiterState
  private final case class Waiting(cb: WaiterCallback) extends WaiterState
  private case object Permitted                        extends WaiterState
  private case object Done                             extends WaiterState

  private sealed trait Reservation
  private case object ZeroReservation                       extends Reservation
  private final case class WaitReservation(waiter: Waiter)  extends Reservation
  private final case class FastReservation(requested: Long) extends Reservation

  private final val unitReservation = Exit.succeed(ZeroReservation)

  private final class Waiter(val requested: Long) extends AtomicReference[WaiterState](Uninitialized)

  private[SemaphoreImpls] sealed trait WaiterQueue {
    def getVolatilePermits: Long
    def getAndAddPermits(delta: Long): Long
    def compareAndSetPermits(expect: Long, update: Long): Boolean

    def getVolatileHead: WaiterQueueNode
    def getVolatileTail: WaiterQueueNode

    def compareAndSetHead(expect: WaiterQueueNode, update: WaiterQueueNode): Boolean
    def compareAndSetTail(expect: WaiterQueueNode, update: WaiterQueueNode): Boolean

    def offer(waiter: Waiter): Unit = {

      @tailrec
      def offerLoop(tail: WaiterQueueNode, nextNode: WaiterQueueNode): Unit = {
        val tailNext = tail.getVolatileNext
        if (tailNext eq null) {
          if (tail.compareAndSetNext(null, nextNode)) {
            // Successfully added node to end of queue, if we're here, that mean nextNode is in the queue
            // Update tail, failure is ok because we can always follow next pointer to the correct position
            compareAndSetTail(tail, nextNode)
          } else {
            // we lose the race, try again from the new tail
            offerLoop(getVolatileTail, nextNode)
          }
        } else if (tailNext eq tail) {
          // we stuck with the old chain
          val latestTail = getVolatileTail
          if (latestTail ne tail) {
            // tail is updated, continue with it
            offerLoop(latestTail, nextNode)
          } else {
            // tail is stale and stuck, we must jump to head
            offerLoop(getVolatileHead, nextNode)
          }
        } else {
          // we can go from here
          offerLoop(tailNext, nextNode)
        }
      }

      offerLoop(getVolatileTail, WaiterQueueNode(waiter))
    }

    private def fixHead(head: WaiterQueueNode, nextHead: WaiterQueueNode): Unit =
      if ((head ne nextHead) && compareAndSetHead(head, nextHead)) {
        // we moved to next head, set old head's next to point back to itself
        head.setVolatileNext(head)
      }

    def poll(): Waiter = {
      @tailrec
      def pollLoop(head: WaiterQueueNode): Waiter = {
        val waiter = head.getVolatileWaiter
        if (waiter ne null) {
          // The head is still valid
          val currentPermits = getVolatilePermits
          if (currentPermits >= waiter.requested) {
            if (compareAndSetPermits(currentPermits, currentPermits - waiter.requested)) {
              // We take requested permits, now let's try to poll this for real
              if (head.compareAndSetWaiter(waiter, null)) {
                // Successfully removed waiter from node, this waiter will be returned
                // Now we need to update head to it's next
                val nextHead = head.getVolatileNext
                if (nextHead ne null) fixHead(head, nextHead)
                waiter
              } else {
                // We lost the race, return the permit back and re-run with new head
                getAndAddPermits(waiter.requested)
                pollLoop(getVolatileHead)
              }
            } else {
              // We lost the race, re-run with new head
              pollLoop(getVolatileHead)
            }
          } else {
            // We can't poll this waiter
            null
          }
        } else {
          // Check the next node of the head
          val nextHead = head.getVolatileNext
          if (nextHead eq null) {
            // If nextHead is null, we are the last node in the queue, we can return null
            null
          } else if (nextHead eq head) {
            // we're stuck with the old chain, re-run with the latest head
            pollLoop(getVolatileHead)
          } else {
            // Otherwise, we move to the next node
            fixHead(head, nextHead)
            pollLoop(nextHead)
          }
        }
      }

      pollLoop(getVolatileHead)
    }

    def peek(): Waiter = {
      @tailrec
      def peekLoop(head: WaiterQueueNode): Waiter = {
        val waiter = head.getVolatileWaiter
        if (waiter ne null) {
          waiter
        } else {
          // Check the next node of the head
          val nextHead = head.getVolatileNext
          if (nextHead eq null) {
            // If nextHead is null, we are the last node in the queue, we can return null
            null
          } else if (nextHead eq head) {
            // we're stuck with the old chain, re-run with the latest head
            peekLoop(getVolatileHead)
          } else {
            // Otherwise, we move to the next node
            fixHead(head, nextHead)
            peekLoop(nextHead)
          }
        }
      }

      peekLoop(getVolatileHead)
    }

    def waiterSize(): Int = {
      @tailrec
      def sizeLoop(head: WaiterQueueNode, acc: Int): Int = {
        val waiter = head.getVolatileWaiter
        if (waiter ne null) {
          // This is still a valid node, we can count it
          val nextHead = head.getVolatileNext
          if (nextHead eq null) {
            // If nextHead is null, we are the last node in the queue, we can return the acc result
            acc + 1
          } else if (nextHead eq head) {
            // If nextHead is the same as head, we're stuck with the old chain, re-run with the latest head
            sizeLoop(getVolatileHead, acc + 1)
          } else {
            // Otherwise, we move to the next node
            sizeLoop(nextHead, acc + 1)
          }
        } else {
          // Check the next node of the head
          val nextHead = head.getVolatileNext
          if (nextHead eq null) {
            // If nextHead is null, we are the last node in the queue, we can return null
            acc
          } else if (nextHead eq head) {
            // If nextHead is the same as head, we're stuck with the old chain, re-run with the latest head
            sizeLoop(getVolatileHead, acc)
          } else {
            // Otherwise, we move to the next node
            sizeLoop(nextHead, acc)
          }
        }
      }

      sizeLoop(getVolatileHead, 0)
    }
  }

  private[SemaphoreImpls] object WaiterQueue {
    def apply(permits: Long): WaiterQueue = {
      val newNode = WaiterQueueNode(null)

      new AtomicLong(permits) with WaiterQueue {
        private val _head = new AtomicReference[WaiterQueueNode](newNode)
        private val _tail = new AtomicReference[WaiterQueueNode](newNode)

        def getVolatilePermits: Long                                  = get()
        def getAndAddPermits(delta: Long): Long                       = getAndAdd(delta)
        def compareAndSetPermits(expect: Long, update: Long): Boolean = compareAndSet(expect, update)

        def getVolatileHead: WaiterQueueNode = _head.get()
        def getVolatileTail: WaiterQueueNode = _tail.get()

        def compareAndSetHead(expect: WaiterQueueNode, update: WaiterQueueNode): Boolean =
          _head.compareAndSet(expect, update)
        def compareAndSetTail(expect: WaiterQueueNode, update: WaiterQueueNode): Boolean =
          _tail.compareAndSet(expect, update)
      }
    }
  }

  private[SemaphoreImpls] sealed trait WaiterQueueNode {
    def getVolatileNext: WaiterQueueNode
    def getVolatileWaiter: Waiter

    def setVolatileNext(next: WaiterQueueNode): Unit
    def setVolatileWaiter(waiter: Waiter): Unit

    def compareAndSetNext(expect: WaiterQueueNode, update: WaiterQueueNode): Boolean
    def compareAndSetWaiter(expect: Waiter, update: Waiter): Boolean
  }

  private[SemaphoreImpls] object WaiterQueueNode {
    def apply(waiter: Waiter): WaiterQueueNode =
      new AtomicReference[WaiterQueueNode](null) with WaiterQueueNode {
        private val _waiter = new AtomicReference[Waiter](waiter)

        def getVolatileNext: WaiterQueueNode = get()
        def getVolatileWaiter: Waiter        = _waiter.get()

        def setVolatileNext(next: WaiterQueueNode): Unit = set(next)
        def setVolatileWaiter(waiter: Waiter): Unit      = _waiter.set(waiter)

        def compareAndSetNext(expect: WaiterQueueNode, update: WaiterQueueNode): Boolean = compareAndSet(expect, update)
        def compareAndSetWaiter(expect: Waiter, update: Waiter): Boolean                 = _waiter.compareAndSet(expect, update)
      }
  }

}
