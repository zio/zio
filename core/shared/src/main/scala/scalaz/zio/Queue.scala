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

import scalaz.zio.internal.Platform
import scalaz.zio.Queue2.internal._
import scalaz.zio.internal.MutableConcurrentQueue

import scala.annotation.tailrec

/**
 * A `Queue2[RA, EA, RB, EB, A, B]` is a lightweight, asynchronous queue into which values of
 * type `A` can be enqueued and of which elements of type `B` can be dequeued. The queue's
 * enqueueing operations may utilize an environment of type `RA` and may fail with errors of
 * type `EA`. The dequeueing operations may utilize an environment of type `RB` and may fail
 * with errors of type `EB`.
 */
trait Queue2[-RA, +EA, -RB, +EB, -A, +B] extends Serializable { self =>
  def capacity: Int

  /**
   * Places one value in the queue.
   */
  def offer(a: A): ZIO[RA, EA, Boolean]

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
  def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean]

  /**
   * Waits until the queue is shutdown.
   * The `IO` returned by this method will not resume until the queue has been shutdown.
   * If the queue is already shutdown, the `IO` will resume right away.
   */
  def awaitShutdown: UIO[Unit]

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  def size: UIO[Int]

  /**
   * Interrupts any fibers that are suspended on `offer` or `take`.
   * Future calls to `offer*` and `take*` will be interrupted immediately.
   */
  def shutdown: UIO[Unit]

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  def take: ZIO[RB, EB, B]

  /**
   * Removes all the values in the queue and returns the list of the values. If the queue
   * is empty returns empty list.
   */
  def takeAll: ZIO[RB, EB, List[B]]

  /**
   * Takes up to max number of values in the queue.
   */
  def takeUpTo(max: Int): ZIO[RB, EB, List[B]]

  /**
   * Take the head option of values in the queue.
   */
  final def poll: ZIO[RB, EB, Option[B]] =
    takeUpTo(1).map(_.headOption)

  /*
   * Transforms elements dequeued from this queue with a function.
   */
  def map[C](f: B => C): Queue2[RA, EA, RB, EB, A, C] =
    new Queue2[RA, EA, RB, EB, A, C] {
      def capacity: Int                                   = self.capacity
      def offer(a: A): ZIO[RA, EA, Boolean]               = self.offer(a)
      def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean] = self.offerAll(as)
      def awaitShutdown: UIO[Unit]                        = self.awaitShutdown
      def size: UIO[Int]                                  = self.size
      def shutdown: UIO[Unit]                             = self.shutdown
      def take: ZIO[RB, EB, C]                            = self.take.map(f)
      def takeAll: ZIO[RB, EB, List[C]]                   = self.takeAll.map(_.map(f))
      def takeUpTo(max: Int): ZIO[RB, EB, List[C]]        = self.takeUpTo(max).map(_.map(f))
    }

  /**
   * Transforms elements dequeued from this queue with an effectful function.
   */
  def mapM[R2 <: RB, E2 >: EB, C](f: B => ZIO[R2, E2, C]): Queue2[RA, EA, R2, E2, A, C] =
    new Queue2[RA, EA, R2, E2, A, C] {
      def capacity: Int                                   = self.capacity
      def offer(a: A): ZIO[RA, EA, Boolean]               = self.offer(a)
      def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean] = self.offerAll(as)
      def awaitShutdown: UIO[Unit]                        = self.awaitShutdown
      def size: UIO[Int]                                  = self.size
      def shutdown: UIO[Unit]                             = self.shutdown
      def take: ZIO[R2, E2, C]                            = self.take.flatMap(f)
      def takeAll: ZIO[R2, E2, List[C]]                   = self.takeAll.flatMap(ZIO.foreach(_)(f))
      def takeUpTo(max: Int): ZIO[R2, E2, List[C]]        = self.takeUpTo(max).flatMap(ZIO.foreach(_)(f))
    }

  /**
   * Creates a new queue from this queue and another. Offering to the composite queue
   * will broadcast the elements to both queues; taking from the composite queue
   * will dequeue elements from both queues and apply the function point-wise.
   *
   * Note that using queues with different strategies may result in surprising behavior.
   * For example, a dropping queue and a bounded queue composed together may apply `f`
   * to different elements.
   */
  def bothWithM[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, R3 <: RB1, E3 >: EB1, D](
    that: Queue2[RA1, EA1, RB1, EB1, A1, C]
  )(f: (B, C) => ZIO[R3, E3, D]): Queue2[RA1, EA1, R3, E3, A1, D] =
    new Queue2[RA1, EA1, R3, E3, A1, D] {
      def capacity: Int = math.min(self.capacity, that.capacity)

      def offer(a: A1): ZIO[RA1, EA1, Boolean]               = self.offer(a).zipWithPar(that.offer(a))(_ && _)
      def offerAll(as: Iterable[A1]): ZIO[RA1, EA1, Boolean] = self.offerAll(as).zipWithPar(that.offerAll(as))(_ && _)

      def awaitShutdown: UIO[Unit] = self.awaitShutdown *> that.awaitShutdown
      def size: UIO[Int]           = self.size.zipWithPar(that.size)(math.max)
      def shutdown: UIO[Unit]      = self.shutdown.zipWithPar(that.shutdown)((_, _) => ())
      def take: ZIO[R3, E3, D]     = self.take.zipPar(that.take).flatMap(f.tupled)

      def takeAll: ZIO[R3, E3, List[D]] =
        self.takeAll.zipPar(that.takeAll).flatMap {
          case (bs, cs) =>
            val bsIt = bs.iterator
            val csIt = cs.iterator

            ZIO.foreach(bsIt.zip(csIt).toList)(f.tupled)
        }

      def takeUpTo(max: Int): ZIO[R3, E3, List[D]] =
        self.takeUpTo(max).zipPar(that.takeUpTo(max)).flatMap {
          case (bs, cs) =>
            val bsIt = bs.iterator
            val csIt = cs.iterator

            ZIO.foreach(bsIt.zip(csIt).toList)(f.tupled)
        }
    }

  /**
   * Like `bothWithM`, but uses a pure function.
   */
  def bothWith[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, D](
    that: Queue2[RA1, EA1, RB1, EB1, A1, C]
  )(f: (B, C) => D): Queue2[RA1, EA1, RB1, EB1, A1, D] =
    bothWithM(that)((a, b) => IO.succeed(f(a, b)))

  /**
   * Like `bothWith`, but tuples the elements instead of applying a function.
   */
  def both[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, D](
    that: Queue2[RA1, EA1, RB1, EB1, A1, C]
  ): Queue2[RA1, EA1, RB1, EB1, A1, (B, C)] =
    bothWith(that)((_, _))

  /**
   * Alias for `both`.
   */
  def &&[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, D](
    that: Queue2[RA1, EA1, RB1, EB1, A1, C]
  ): Queue2[RA1, EA1, RB1, EB1, A1, (B, C)] =
    both(that)

  /**
   * Transforms elements enqueued into this queue with a pure function.
   */
  def contramap[C](f: C => A): Queue2[RA, EA, RB, EB, C, B] =
    new Queue2[RA, EA, RB, EB, C, B] {
      def capacity: Int = self.capacity

      def offer(c: C): ZIO[RA, EA, Boolean] =
        self.offer(f(c))

      def offerAll(as: Iterable[C]): ZIO[RA, EA, Boolean] = self.offerAll(as.map(f))

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def take: ZIO[RB, EB, B]                     = self.take
      def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
      def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
    }

  /**
   * Transforms elements enqueued into this queue with an effectful function.
   */
  def contramapM[RA2 <: RA, EA2 >: EA, C](f: C => ZIO[RA2, EA2, A]): Queue2[RA2, EA2, RB, EB, C, B] =
    new Queue2[RA2, EA2, RB, EB, C, B] {
      def capacity: Int = self.capacity

      def offer(c: C): ZIO[RA2, EA2, Boolean] =
        f(c).flatMap(self.offer)

      def offerAll(as: Iterable[C]): ZIO[RA2, EA2, Boolean] =
        ZIO.foreach(as)(f).flatMap(self.offerAll)

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def take: ZIO[RB, EB, B]                     = self.take
      def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
      def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
    }
}

object Queue2 {
  implicit class InvariantQueue2Ops[RA, EA, RB, EB, A, B](private val self: Queue2[RA, EA, RB, EB, A, B]) {

    /**
     * Applies a filter to elements enqueued into this queue. Elements that do not
     * pass the filter will be immediately dropped.
     */
    def filterInput(f: A => Boolean): Queue2[RA, EA, RB, EB, A, B] =
      new Queue2[RA, EA, RB, EB, A, B] {
        def capacity: Int = self.capacity

        def offer(a: A): ZIO[RA, EA, Boolean] =
          if (f(a)) self.offer(a)
          else IO.succeed(false)

        def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean] = {
          val filtered = as filter f

          if (filtered.isEmpty) ZIO.succeed(false)
          else self.offerAll(filtered)
        }

        def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
        def size: UIO[Int]                           = self.size
        def shutdown: UIO[Unit]                      = self.shutdown
        def take: ZIO[RB, EB, B]                     = self.take
        def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
        def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
      }

    /**
     * Like `filterInput`, but uses an effectful function to filter the elements.
     */
    def filterInputM[R2 <: RA, E2 >: EA](f: A => ZIO[R2, E2, Boolean]): Queue2[R2, E2, RB, EB, A, B] =
      new Queue2[R2, E2, RB, EB, A, B] {
        def capacity: Int = self.capacity

        def offer(a: A): ZIO[R2, E2, Boolean] =
          f(a) flatMap {
            if (_) self.offer(a)
            else IO.succeed(false)
          }

        def offerAll(as: Iterable[A]): ZIO[R2, E2, Boolean] =
          ZIO.foreach(as)(a => f(a).map(if (_) Some(a) else None)).flatMap { maybeAs =>
            val filtered = maybeAs.flatten
            if (filtered.isEmpty) ZIO.succeed(false)
            else self.offerAll(filtered)
          }

        def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
        def size: UIO[Int]                           = self.size
        def shutdown: UIO[Unit]                      = self.shutdown
        def take: ZIO[RB, EB, B]                     = self.take
        def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
        def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
      }
  }

  private def unsafeCreate[A](
    queue: MutableConcurrentQueue[A],
    takers: MutableConcurrentQueue[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    strategy: Strategy[A]
  ): Queue2[Any, Nothing, Any, Nothing, A, A] = new Queue2[Any, Nothing, Any, Nothing, A, A] {

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
    private final def unsafeCompleteTakers(platform: Platform): Unit =
      pollTakersThenQueue() match {
        case None =>
        case Some((p, a)) =>
          unsafeCompletePromise(p, a, platform)
          strategy.unsafeOnQueueEmptySpace(queue, platform)
          unsafeCompleteTakers(platform)
      }

    private final def removeTaker(taker: Promise[Nothing, A]): UIO[Unit] = IO.effectTotal(unsafeRemove(takers, taker))

    final val capacity: Int = queue.capacity

    final def offer(a: A): UIO[Boolean] = offerAll(List(a))

    final def offerAll(as: Iterable[A]): UIO[Boolean] =
      for {
        _ <- checkShutdownState

        remaining <- IO.effectTotalWith { platform =>
                      val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else List.empty
                      val (forTakers, remaining) = as.splitAt(pTakers.size)
                      (pTakers zip forTakers).foreach {
                        case (taker, item) => unsafeCompletePromise(taker, item, platform)
                      }
                      remaining
                    }

        added <- if (remaining.nonEmpty) {
                  // not enough takers, offer to the queue
                  for {
                    surplus <- IO.effectTotalWith { platform =>
                                val as = unsafeOfferAll(queue, remaining.toList)
                                unsafeCompleteTakers(platform)
                                as
                              }
                    res <- if (surplus.isEmpty) IO.succeed(true)
                          else
                            strategy.handleSurplus(surplus, queue) <* IO.effectTotalWith(
                              platform => unsafeCompleteTakers(platform)
                            )
                  } yield res
                } else IO.succeed(true)
      } yield added

    final val awaitShutdown: UIO[Unit] = shutdownHook.await

    final val size: UIO[Int] = checkShutdownState.map(_ => queue.size - takers.size + strategy.surplusSize)

    final val shutdown: UIO[Unit] =
      IO.whenM(shutdownHook.succeed(()))(
          IO.effectTotal(unsafePollAll(takers)) >>= (IO.foreachPar(_)(_.interrupt) *> strategy.shutdown)
        )
        .uninterruptible

    final val take: UIO[A] =
      for {
        _ <- checkShutdownState

        item <- IO.effectTotalWith { platform =>
                 val item = queue.poll(null.asInstanceOf[A])
                 if (item != null) strategy.unsafeOnQueueEmptySpace(queue, platform)
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
                a <- (IO.effectTotalWith { platform =>
                      takers.offer(p)
                      unsafeCompleteTakers(platform)
                    } *> p.await).onInterrupt(removeTaker(p))
              } yield a
      } yield a

    final val takeAll: UIO[List[A]] =
      for {
        _ <- checkShutdownState

        as <- IO.effectTotalWith { platform =>
               val as = unsafePollAll(queue)
               strategy.unsafeOnQueueEmptySpace(queue, platform)
               as
             }
      } yield as

    final def takeUpTo(max: Int): UIO[List[A]] =
      for {
        _ <- checkShutdownState

        as <- IO.effectTotalWith { platform =>
               val as = unsafePollN(queue, max)
               strategy.unsafeOnQueueEmptySpace(queue, platform)
               as
             }
      } yield as
  }

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

    final def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A, platform: Platform): Unit =
      p.unsafeDone(IO.succeed(a), platform.executor)

    sealed trait Strategy[A] {
      def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): UIO[Boolean]

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], platform: Platform): Unit

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
        IO.effectTotal(unsafeSlidingOffer(as)).map(_ => !loss)
      }

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], platform: Platform): Unit = ()

      final def surplusSize: Int = 0

      final def shutdown: UIO[Unit] = IO.unit
    }

    case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      final def handleSurplus(as: List[A], queue: MutableConcurrentQueue[A]): UIO[Boolean] = IO.succeed(false)

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], platform: Platform): Unit = ()

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
          _ <- (IO.effectTotalWith { platform =>
                unsafeOffer(as, p)
                unsafeOnQueueEmptySpace(queue, platform)
              } *> p.await).onInterrupt(IO.effectTotal(unsafeRemove(p)))
        } yield true
      }

      final def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A], platform: Platform): Unit = {
        @tailrec
        def unsafeMovePutters(): Unit =
          if (!queue.isFull()) {
            putters.poll(null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)]) match {
              case null =>
              case putter @ (a, p, lastItem) =>
                if (queue.offer(a)) {
                  if (lastItem) unsafeCompletePromise(p, true, platform)
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
          putters <- IO.effectTotal(unsafePollAll(putters))
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
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, BackPressure()))

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
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Sliding()))

  /**
   * Makes a new bounded queue with the dropping strategy.
   * When the capacity of the queue is reached, new elements will be dropped.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[scalaz.zio.internal.impls.RingBuffer]].
   */
  final def dropping[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Dropping()))

  /**
   * Makes a new unbounded queue.
   */
  final def unbounded[A]: UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.unbounded[A]).flatMap(createQueue(_, Dropping()))

  private final def createQueue[A](queue: MutableConcurrentQueue[A], strategy: Strategy[A]): UIO[Queue[A]] =
    Promise
      .make[Nothing, Unit]
      .map(p => unsafeCreate(queue, MutableConcurrentQueue.unbounded[Promise[Nothing, A]], p, strategy))
}
