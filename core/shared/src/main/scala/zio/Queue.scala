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

package zio

import zio.Queue.internal._
import zio.internal.MutableConcurrentQueue

import scala.annotation.tailrec

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

    final def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Unit =
      p.unsafeDone(IO.succeed(a))

    sealed trait Strategy[A] {
      def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A], checkShutdownState: UIO[Unit]): UIO[Boolean]

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit

      def surplusSize: Int

      def shutdown: UIO[Unit]
    }

    case class Sliding[A]() extends Strategy[A] {
      final def handleSurplus(
        as: List[A],
        queue: MutableConcurrentQueue[A],
        checkShutdownState: UIO[Unit]
      ): UIO[Boolean] = {
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
        IO.effectTotal(unsafeSlidingOffer(as)).map(_ => !loss)
      }

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit = ()

      final def surplusSize: Int = 0

      final def shutdown: UIO[Unit] = IO.unit
    }

    case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      final def handleSurplus(
        as: List[A],
        queue: MutableConcurrentQueue[A],
        checkShutdownState: UIO[Unit]
      ): UIO[Boolean] = IO.succeed(false)

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit = ()

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

      final def handleSurplus(
        as: List[A],
        queue: MutableConcurrentQueue[A],
        checkShutdownState: UIO[Unit]
      ): UIO[Boolean] =
        UIO.effectSuspendTotal {
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
            _ <- (IO.effectTotal {
                  unsafeOffer(as, p)
                  unsafeOnQueueEmptySpace(queue)
                } *> checkShutdownState *> p.await).onInterrupt(IO.effectTotal(unsafeRemove(p)))
          } yield true
        }

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit = {
        @tailrec
        def unsafeMovePutters(): Unit =
          if (!queue.isFull()) {
            putters.poll(null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)]) match {
              case null =>
              case putter @ (a, p, lastItem) =>
                if (queue.offer(a)) {
                  if (lastItem) unsafeCompletePromise(p, true)
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
          fiberId <- ZIO.fiberId
          putters <- IO.effectTotal(unsafePollAll(putters))
          _       <- IO.foreachPar(putters) { case (_, p, lastItem) => if (lastItem) p.interruptAs(fiberId) else IO.unit }
        } yield ()
    }
  }
  private def unsafeCreate[A](
    queue: MutableConcurrentQueue[A],
    takers: MutableConcurrentQueue[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    strategy: Strategy[A]
  ): Queue[A] = new ZQueue[Any, Nothing, Any, Nothing, A, A] {

    private final val checkShutdownState: UIO[Unit] =
      shutdownHook.poll.flatMap(_.fold[UIO[Unit]](IO.unit)(_ => IO.interrupt))

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
    private final def unsafeCompleteTakers(): Unit =
      pollTakersThenQueue() match {
        case None =>
        case Some((p, a)) =>
          unsafeCompletePromise(p, a)
          strategy.unsafeOnQueueEmptySpace(queue)
          unsafeCompleteTakers()
      }

    private final def removeTaker(taker: Promise[Nothing, A]): UIO[Unit] = IO.effectTotal(unsafeRemove(takers, taker))

    final val capacity: Int = queue.capacity

    final def offer(a: A): UIO[Boolean] = offerAll(List(a))

    final def offerAll(as: Iterable[A]): UIO[Boolean] =
      UIO.effectSuspendTotal {
        for {
          _ <- checkShutdownState

          remaining <- IO.effectTotal {
                        val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else List.empty
                        val (forTakers, remaining) = as.splitAt(pTakers.size)
                        (pTakers zip forTakers).foreach {
                          case (taker, item) => unsafeCompletePromise(taker, item)
                        }
                        remaining
                      }

          added <- if (remaining.nonEmpty) {
                    // not enough takers, offer to the queue
                    for {
                      surplus <- IO.effectTotal {
                                  val as = unsafeOfferAll(queue, remaining.toList)
                                  unsafeCompleteTakers()
                                  as
                                }
                      res <- if (surplus.isEmpty) IO.succeed(true)
                            else
                              strategy.handleSurplus(surplus, queue, checkShutdownState) <*
                                IO.effectTotal(unsafeCompleteTakers())
                    } yield res
                  } else IO.succeed(true)
        } yield added
      }

    final val awaitShutdown: UIO[Unit] = shutdownHook.await

    final val size: UIO[Int] = checkShutdownState.map(_ => queue.size() - takers.size() + strategy.surplusSize)

    final val shutdown: UIO[Unit] =
      ZIO.fiberId.flatMap(
        fiberId =>
          IO.whenM(shutdownHook.succeed(()))(
              IO.effectTotal(unsafePollAll(takers)) >>= (IO.foreachPar(_)(_.interruptAs(fiberId)) *> strategy.shutdown)
            )
            .uninterruptible
      )

    final val isShutdown: UIO[Boolean] = shutdownHook.poll.map(_.isDefined)

    final val take: UIO[A] =
      UIO.effectSuspendTotal {
        for {
          _ <- checkShutdownState

          item <- IO.effectTotal {
                   val item = queue.poll(null.asInstanceOf[A])
                   if (item != null) strategy.unsafeOnQueueEmptySpace(queue)
                   item
                 }

          a <- if (item != null) IO.succeed(item)
              else
                for {
                  p <- Promise.make[Nothing, A]
                  // add the promise to takers, then:
                  // - try take again in case a value was added since
                  // - wait for the promise to be completed
                  // - clean up resources in case of interruption
                  a <- (IO.effectTotal {
                        takers.offer(p)
                        unsafeCompleteTakers()
                      } *> checkShutdownState *> p.await).onInterrupt(removeTaker(p))
                } yield a
        } yield a
      }

    final val takeAll: UIO[List[A]] =
      UIO.effectSuspendTotal {
        for {
          _ <- checkShutdownState

          as <- IO.effectTotal {
                 val as = unsafePollAll(queue)
                 strategy.unsafeOnQueueEmptySpace(queue)
                 as
               }
        } yield as
      }

    final def takeUpTo(max: Int): UIO[List[A]] =
      UIO.effectSuspendTotal {
        for {
          _ <- checkShutdownState

          as <- IO.effectTotal {
                 val as = unsafePollN(queue, max)
                 strategy.unsafeOnQueueEmptySpace(queue)
                 as
               }
        } yield as
      }
  }

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[zio.internal.impls.RingBuffer]].
   *
   * @param requestedCapacity capacity of the `Queue`
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  final def bounded[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, BackPressure()))

  /**
   * Makes a new bounded queue with the dropping strategy.
   * When the capacity of the queue is reached, new elements will be dropped.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[zio.internal.impls.RingBuffer]].
   *
   * @param requestedCapacity capacity of the `Queue`
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  final def dropping[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Dropping()))

  /**
   * Makes a new bounded queue with sliding strategy.
   * When the capacity of the queue is reached, new elements will be added and the old elements
   * will be dropped.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[zio.internal.impls.RingBuffer]].
   *
   * @param requestedCapacity capacity of the `Queue`
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  final def sliding[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Sliding()))

  /**
   * Makes a new unbounded queue.
   *
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  final def unbounded[A]: UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.unbounded[A]).flatMap(createQueue(_, Dropping()))

  private final def createQueue[A](queue: MutableConcurrentQueue[A], strategy: Strategy[A]): UIO[Queue[A]] =
    Promise
      .make[Nothing, Unit]
      .map(p => unsafeCreate(queue, MutableConcurrentQueue.unbounded[Promise[Nothing, A]], p, strategy))
}
