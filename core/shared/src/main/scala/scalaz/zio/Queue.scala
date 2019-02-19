/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scalaz.zio

import scala.annotation.tailrec
import scalaz.zio.internal.Platform
import scalaz.zio.Queue.internal._
import scalaz.zio.internal.MutableConcurrentQueue

/**
 *  A `Queue[A]` is a lightweight, asynchronous queue for values of type `A`.
 */
class Queue[A] private (
  queue: MutableConcurrentQueue[A],
  takers: MutableConcurrentQueue[Promise[Nothing, A]],
  shutdownHook: Ref[Option[UIO[Unit]]],
  strategy: Strategy[A]
) extends Serializable {

  private final val checkShutdownState: UIO[Unit] =
    shutdownHook.get.flatMap(_.fold[UIO[Unit]](IO.interrupt)(_ => IO.unit))

  @tailrec
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
            pollTakersThenQueue()
          case a => Some((taker, a))
        }
      }
    } else None

  @tailrec
  private final def unsafeCompleteTakers(context: Platform): Unit =
    pollTakersThenQueue() match {
      case None =>
      case Some((p, a)) =>
        unsafeCompletePromise(p, a, context)
        strategy.unsafeOnQueueEmptySpace(queue, context)
        unsafeCompleteTakers(context)
    }

  private final def removeTaker(taker: Promise[Nothing, A]): UIO[Unit] = IO.sync(unsafeRemove(takers, taker))

  final val capacity: Int = queue.capacity

  /**
   * Places one value in the queue.
   */
  final def offer(a: A): UIO[Boolean] = offerAll(List(a))

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
  final def offerAll(as: Iterable[A]): UIO[Boolean] =
    for {
      _ <- checkShutdownState

      remaining <- IO.sync0 { context =>
                    val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else List.empty
                    val (forTakers, remaining) = as.splitAt(pTakers.size)
                    (pTakers zip forTakers).foreach {
                      case (taker, item) => unsafeCompletePromise(taker, item, context)
                    }
                    remaining
                  }

      added <- if (remaining.nonEmpty) {
                // not enough takers, offer to the queue
                for {
                  surplus <- IO.sync0 { context =>
                              val as = unsafeOfferAll(queue, remaining.toList)
                              unsafeCompleteTakers(context)
                              as
                            }
                  res <- if (surplus.isEmpty) IO.succeed(true)
                        else
                          strategy.handleSurplus(surplus, queue) <* IO.sync0(
                            context => unsafeCompleteTakers(context)
                          )
                } yield res
              } else IO.succeed(true)
    } yield added

  /**
   * Waits until the queue is shutdown.
   * The `IO` returned by this method will not resume until the queue has been shutdown.
   * If the queue is already shutdown, the `IO` will resume right away.
   */
  final val awaitShutdown: UIO[Unit] =
    for {
      p  <- Promise.make[Nothing, Unit]
      io = p.succeed(()).void
      _ <- IO.flatten(shutdownHook.modify {
            case None       => (io, None)
            case Some(hook) => (IO.unit, Some(hook *> io))
          })
      _ <- p.await
    } yield ()

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  final val size: UIO[Int] = checkShutdownState.map(_ => queue.size - takers.size + strategy.surplusSize)

  /**
   * Interrupts any fibers that are suspended on `offer` or `take`.
   * Future calls to `offer*` and `take*` will be interrupted immediately.
   */
  final val shutdown: UIO[Unit] = (for {
    hook <- shutdownHook.modify {
             case None       => (IO.unit, None)
             case Some(hook) => (hook, None)
           }
    takers <- IO.sync(unsafePollAll(takers))
    _      <- IO.foreachPar(takers)(_.interrupt) *> hook
    _      <- strategy.shutdown
  } yield ()).uninterruptible

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  final val take: UIO[A] =
    for {
      _ <- checkShutdownState

      item <- IO.sync0 { context =>
               val item = queue.poll(null.asInstanceOf[A])
               if (item != null) strategy.unsafeOnQueueEmptySpace(queue, context)
               item
             }

      a <- if (item != null) IO.succeedLazy(item)
          else
            for {
              p <- Promise.make[Nothing, A]
              // add the promise to takers, then:
              // - try take again in case a value was added since
              // - wait for the promise to be completed
              // - clean up resources in case of interruption
              a <- (IO.sync0 { context =>
                    takers.offer(p)
                    unsafeCompleteTakers(context)
                  } *> p.await).onInterrupt(removeTaker(p))
            } yield a
    } yield a

  /**
   * Removes all the values in the queue and returns the list of the values. If the queue
   * is empty returns empty list.
   */
  final val takeAll: UIO[List[A]] =
    for {
      _ <- checkShutdownState

      as <- IO.sync0 { context =>
             val as = unsafePollAll(queue)
             strategy.unsafeOnQueueEmptySpace(queue, context)
             as
           }
    } yield as

  /**
   * Takes up to max number of values in the queue.
   */
  final def takeUpTo(max: Int): UIO[List[A]] =
    for {
      _ <- checkShutdownState

      as <- IO.sync0 { context =>
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

    final def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A, context: Platform): Unit =
      p.unsafeDone(IO.succeed(a), context.executor)

    sealed trait Strategy[A] {
      def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): UIO[Boolean]

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: Platform): Unit

      def surplusSize: Int

      def shutdown: UIO[Unit]
    }

    case class Sliding[A]() extends Strategy[A] {
      final def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): UIO[Boolean] = {
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

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: Platform): Unit = ()

      final def surplusSize: Int = 0

      final def shutdown: UIO[Unit] = IO.unit
    }

    case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      final def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): UIO[Boolean] = IO.succeed(false)

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: Platform): Unit = ()

      final def surplusSize: Int = 0

      final def shutdown: UIO[Unit] = IO.unit
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

      final def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): UIO[Boolean] = {
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
          _ <- (IO.sync0 { context =>
                unsafeOffer(as, p)
                unsafeOnQueueEmptySpace(queue, context)
              } *> p.await).onInterrupt(IO.sync(unsafeRemove(p)))
        } yield true
      }

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], context: Platform): Unit = {
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
                  unsafeMovePutters()
                }
            }
          }

        unsafeMovePutters()
      }

      final def surplusSize: Int = putters.size()

      final def shutdown: UIO[Unit] =
        for {
          putters <- IO.sync(unsafePollAll(putters))
          _       <- IO.foreachPar(putters) { case (_, p, lastItem) => if (lastItem) p.interrupt else IO.unit }
        } yield ()
    }
  }

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[scalaz.zio.internal.impls.RingBuffer]].
   */
  final def bounded[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.sync(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, BackPressure()))

  /**
   * Makes a new bounded queue with sliding strategy.
   * When the capacity of the queue is reached, new elements will be added and the old elements
   * will be dropped.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[scalaz.zio.internal.impls.RingBuffer]].
   */
  final def sliding[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.sync(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Sliding()))

  /**
   * Makes a new bounded queue with the dropping strategy.
   * When the capacity of the queue is reached, new elements will be dropped.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[scalaz.zio.internal.impls.RingBuffer]].
   */
  final def dropping[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.sync(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Dropping()))

  /**
   * Makes a new unbounded queue.
   */
  final def unbounded[A]: UIO[Queue[A]] =
    IO.sync(MutableConcurrentQueue.unbounded[A]).flatMap(createQueue(_, Dropping()))

  private final def createQueue[A](queue: MutableConcurrentQueue[A], strategy: Strategy[A]): UIO[Queue[A]] =
    Ref
      .make[Option[UIO[Unit]]](Some(IO.unit))
      .map(ref => new Queue[A](queue, MutableConcurrentQueue.unbounded[Promise[Nothing, A]], ref, strategy))

}
