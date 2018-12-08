// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz.zio

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scalaz.zio.Queue.internal._
import scalaz.zio.internal.MutableConcurrentQueue

/**
 *  A `Queue[A]` is a lightweight, asynchronous queue for values of type `A`.
 */
class Queue[A] private (
  queue: MutableConcurrentQueue[A],
  takers: MutableConcurrentQueue[Promise[Nothing, A]],
  shutdownHook: Ref[Option[IO[Nothing, Unit]]],
  strategy: Strategy[A]
) extends Serializable {

  private final val checkShutdownState: IO[Nothing, Unit] =
    shutdownHook.get.flatMap(_.fold[IO[Nothing, Unit]](IO.interrupt)(_ => IO.unit))

  private final def pollTakersThenQueue(): Option[(Promise[Nothing, A], A)] =
    // check if there is both a taker and an item in the queue, starting by the taker
    if (!queue.isEmpty()) {
      val nullTaker = null.asInstanceOf[Promise[Nothing, A]]
      val taker     = takers.poll(nullTaker)
      if (taker == nullTaker) {
        None
      } else {
        queue.poll(null.asInstanceOf[A]) match {
          case null =>
            unsafeOfferAll(takers, taker :: unsafePollAll(takers))
            None
          case a => Some((taker, a))
        }
      }
    } else None

  @tailrec
  private final def unsafeCompleteTakers(context: ExecutionContext): Unit =
    pollTakersThenQueue() match {
      case None =>
      case Some((p, a)) =>
        unsafeCompletePromise(p, a, context)
        strategy.unsafeOnQueueEmptySpace(queue, context)
        unsafeCompleteTakers(context)
    }

  private final def removeTaker(taker: Promise[Nothing, A]): IO[Nothing, Unit] = IO.sync(unsafeRemove(takers, taker))

  /**
   * For performance reasons, the actual capacity of the queue is the next power of 2 of the requested capacity.
   */
  final val capacity: Int = queue.capacity

  /**
   * Places one value in the queue.
   */
  final def offer(a: A): IO[Nothing, Boolean] = offerAll(List(a))

  /**
   * For Bounded Queue: uses the `BackPressure` Strategy, places the values in the queue and returns always true
   * If the queue has reached capacity, then
   * the fiber performing the `offerAll` will be suspended until there is room in
   * the queue.
   *
   * For Unbounded Queue:
   * Places all values in the queue and returns true.
   *
   * For Sliding Queue: uses `Sliding` Strategy
   * If there is a room in the queue, it places the values and returns true otherwise it removed the old elements and
   * enqueues the new ones
   *
   * For Dropping Queue: uses `Dropping` Strategy,
   * It places the values in the queue but if there is no room it will not enqueue them and returns false
   *
   */
  final def offerAll(as: Iterable[A]): IO[Nothing, Boolean] =
    for {
      _ <- checkShutdownState

      remaining <- IO.syncSubmit { context =>
                    val pTakers                = unsafePollN(takers, as.size)
                    val (forTakers, remaining) = as.splitAt(pTakers.size)
                    (pTakers zip forTakers).foreach {
                      case (taker, item) => unsafeCompletePromise(taker, item, context)
                    }
                    remaining
                  }

      added <- if (remaining.nonEmpty) {
                // not enough takers, offer to the queue
                for {
                  surplus <- IO.syncSubmit { context =>
                              val as = unsafeOfferAll(queue, remaining.toList)
                              unsafeCompleteTakers(context)
                              as
                            }
                  res <- if (surplus.isEmpty) IO.now(true)
                        else
                          strategy.handleSurplus(surplus, queue) <* IO.syncSubmit(
                            context => unsafeCompleteTakers(context)
                          )
                } yield res
              } else IO.now(true)
    } yield added

  /**
   * Waits until the queue is shutdown.
   * The `IO` returned by this method will not resume until the queue has been shutdown.
   * If the queue is already shutdown, the `IO` will resume right away.
   */
  final val awaitShutdown: IO[Nothing, Unit] =
    for {
      p  <- Promise.make[Nothing, Unit]
      io = p.complete(()).void
      _ <- IO.flatten(shutdownHook.modify {
            case None       => (io, None)
            case Some(hook) => (IO.unit, Some(hook *> io))
          })
      _ <- p.get
    } yield ()

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  final val size: IO[Nothing, Int] = checkShutdownState.map(_ => queue.size - takers.size + strategy.surplusSize)

  /**
   * Interrupts any fibers that are suspended on `offer` or `take`.
   * Future calls to `offer*` and `take*` will be interrupted immediately.
   */
  final val shutdown: IO[Nothing, Unit] = (for {
    hook <- shutdownHook.modify {
             case None       => (IO.unit, None)
             case Some(hook) => (hook, None)
           }
    takers <- IO.sync(unsafePollAll(takers))
    _      <- IO.parTraverse(takers)(_.interrupt) *> hook
    _      <- strategy.shutdown
  } yield ()).uninterruptibly

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  final val take: IO[Nothing, A] =
    for {
      _ <- checkShutdownState

      item <- IO.syncSubmit { context =>
               val item = queue.poll(null.asInstanceOf[A])
               if (item != null) strategy.unsafeOnQueueEmptySpace(queue, context)
               item
             }

      a <- if (item != null) IO.point(item)
          else
            for {
              p <- Promise.make[Nothing, A]
              // add the promise to takers, then try take again in case a value was added since
              a <- IO.syncSubmit { context =>
                    val a = takers.offer(p)
                    unsafeCompleteTakers(context)
                    a
                  }
                  // wait for the promise to be completed
                    .flatMap(_ => p.get)
                    // clean up resources in case of interruption
                    .ensuring(p.poll.void <> removeTaker(p))
            } yield a
    } yield a

  /**
   * Removes all the values in the queue and returns the list of the values. If the queue
   * is empty returns empty list.
   */
  final val takeAll: IO[Nothing, List[A]] =
    for {
      _ <- checkShutdownState

      as <- IO.syncSubmit { context =>
             val as = unsafePollAll(queue)
             strategy.unsafeOnQueueEmptySpace(queue, context)
             as
           }
    } yield as

  /**
   * Takes up to max number of values in the queue.
   */
  final def takeUpTo(max: Int): IO[Nothing, List[A]] =
    for {
      _ <- checkShutdownState

      as <- IO.syncSubmit { context =>
             val as = unsafePollN(queue, max)
             strategy.unsafeOnQueueEmptySpace(queue, context)
             as
           }
    } yield as

}

object Queue {

  private[zio] object internal {

    /**
     * Poll all items from the queue
     */
    final def unsafePollAll[A](q: MutableConcurrentQueue[A]): List[A] = {
      @tailrec
      def poll(as: List[A]): List[A] =
        q.poll(null.asInstanceOf[A]) match {
          case null => as
          case a    => poll(a :: as)
        }
      poll(List.empty[A]).reverse
    }

    /**
     * Poll n items from the queue
     */
    final def unsafePollN[A](q: MutableConcurrentQueue[A], max: Int): List[A] = {
      @tailrec
      def poll(as: List[A], n: Int): List[A] =
        if (n < 1) as
        else
          q.poll(null.asInstanceOf[A]) match {
            case null => as
            case a    => poll(a :: as, n - 1)
          }
      poll(List.empty[A], max).reverse
    }

    /**
     * Offer items to the queue
     */
    final def unsafeOfferAll[A](q: MutableConcurrentQueue[A], as: List[A]): List[A] = {
      @tailrec
      def offerAll(as: List[A]): List[A] =
        as match {
          case Nil          => as
          case head :: tail => if (q.offer(head)) offerAll(tail) else as
        }
      offerAll(as)
    }

    /**
     * Remove an item from the queue
     */
    final def unsafeRemove[A](q: MutableConcurrentQueue[A], a: A): Unit = {
      unsafeOfferAll(q, unsafePollAll(q).filterNot(_ == a))
      ()
    }

    final def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A, context: ExecutionContext): Unit =
      p.unsafeDone(ExitResult.succeeded(a), context)

    sealed trait Strategy[A] {
      def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): IO[Nothing, Boolean]

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: ExecutionContext): Unit

      def surplusSize: Int

      def shutdown: IO[Nothing, Unit]
    }

    case class Sliding[A]() extends Strategy[A] {
      final def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): IO[Nothing, Boolean] = {
        @tailrec
        def unsafeSlidingOffer(as: List[A]): Unit =
          as match {
            case Nil                      =>
            case _ if queue.capacity == 0 => // early exit if the queue has 0 capacity
            case as @ head :: tail        =>
              // poll one, then try offering again
              queue.poll(null.asInstanceOf[A])
              if (queue.offer(head)) unsafeSlidingOffer(tail) else unsafeSlidingOffer(as)
          }
        val loss = queue.capacity - queue.size() < as.size
        IO.sync(unsafeSlidingOffer(as)).map(_ => !loss)
      }

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: ExecutionContext): Unit = ()

      final def surplusSize: Int = 0

      final def shutdown: IO[Nothing, Unit] = IO.unit
    }

    case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      final def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): IO[Nothing, Boolean] = IO.now(false)

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: ExecutionContext): Unit = ()

      final def surplusSize: Int = 0

      final def shutdown: IO[Nothing, Unit] = IO.unit
    }

    case class BackPressure[A]() extends Strategy[A] {
      // A is an item to add
      // Promise[Nothing, Boolean] is the promise completing the whole offerAll
      // Boolean indicates if it's the last item to offer (promise should be completed once this item is added)
      private val putters = MutableConcurrentQueue.unbounded[(A, Promise[Nothing, Boolean], Boolean)]

      private final def unsafeRemove(p: Promise[Nothing, Boolean]): Unit = {
        unsafeOfferAll(putters, unsafePollAll(putters).filterNot(_._2 == p))
        ()
      }

      final def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): IO[Nothing, Boolean] = {
        @tailrec
        def unsafeOffer(as: List[A], p: Promise[Nothing, Boolean]): Unit =
          as match {
            case Nil =>
            case head :: tail if tail.isEmpty =>
              putters.offer((head, p, true))
              ()
            case head :: tail =>
              putters.offer((head, p, false))
              unsafeOffer(tail, p)
          }

        for {
          p <- Promise.make[Nothing, Boolean]
          _ <- (IO.syncSubmit { context =>
                unsafeOffer(as, p)
                unsafeOnQueueEmptySpace(queue, context)
              } *> p.get).ensuring(p.poll.void <> IO.sync(unsafeRemove(p)))
        } yield true
      }

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: ExecutionContext): Unit = {
        @tailrec
        def unsafeMovePutters(): Unit =
          if (!queue.isFull()) {
            putters.poll(null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)]) match {
              case null =>
              case putter @ (a, p, lastItem) =>
                if (queue.offer(a)) {
                  if (lastItem) unsafeCompletePromise(p, true, context)
                  unsafeMovePutters()
                } else {
                  unsafeOfferAll(putters, putter :: unsafePollAll(putters))
                  ()
                }
            }
          }

        unsafeMovePutters()
      }

      final def surplusSize: Int = putters.size()

      final def shutdown: IO[Nothing, Unit] =
        for {
          putters <- IO.sync(unsafePollAll(putters))
          _       <- IO.parTraverse(putters) { case (_, p, lastItem) => if (lastItem) p.interrupt else IO.unit }
        } yield ()
    }
  }

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   */
  final def bounded[A](requestedCapacity: Int): IO[Nothing, Queue[A]] =
    createQueue(MutableConcurrentQueue.bounded[A](requestedCapacity), BackPressure())

  /**
   * Makes a new bounded queue with sliding strategy.
   * When the capacity of the queue is reached, new elements will be added and the old elements
   * will be dropped.
   */
  final def sliding[A](requestedCapacity: Int): IO[Nothing, Queue[A]] =
    createQueue(MutableConcurrentQueue.bounded[A](requestedCapacity), Sliding())

  /**
   * Makes a new bounded queue with the dropping strategy.
   * When the capacity of the queue is reached, new elements will be dropped.
   */
  final def dropping[A](requestedCapacity: Int): IO[Nothing, Queue[A]] =
    createQueue(MutableConcurrentQueue.bounded[A](requestedCapacity), Dropping())

  /**
   * Makes a new unbounded queue.
   */
  final def unbounded[A]: IO[Nothing, Queue[A]] = createQueue(MutableConcurrentQueue.unbounded[A], Dropping())

  private final def createQueue[A](queue: MutableConcurrentQueue[A], strategy: Strategy[A]): IO[Nothing, Queue[A]] =
    Ref[Option[IO[Nothing, Unit]]](Some(IO.unit))
      .map(ref => new Queue[A](queue, MutableConcurrentQueue.unbounded[Promise[Nothing, A]], ref, strategy))

}
