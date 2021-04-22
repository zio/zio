/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.ZQueue.internal._
import zio.internal.MutableConcurrentQueue

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
 * A `ZQueue[RA, RB, EA, EB, A, B]` is a lightweight, asynchronous queue into which values of
 * type `A` can be enqueued and of which elements of type `B` can be dequeued. The queue's
 * enqueueing operations may utilize an environment of type `RA` and may fail with errors of
 * type `EA`. The dequeueing operations may utilize an environment of type `RB` and may fail
 * with errors of type `EB`.
 */
abstract class ZQueue[-RA, -RB, +EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * Waits until the queue is shutdown.
   * The `IO` returned by this method will not resume until the queue has been shutdown.
   * If the queue is already shutdown, the `IO` will resume right away.
   */
  def awaitShutdown: UIO[Unit]

  /**
   * How many elements can hold in the queue
   */
  def capacity: Int

  /**
   * `true` if `shutdown` has been called.
   */
  def isShutdown: UIO[Boolean]

  /**
   * Places one value in the queue.
   */
  def offer(a: A): ZIO[RA, EA, Boolean]

  /**
   * For Bounded Queue: uses the `BackPressure` Strategy, places the values in the queue and always returns true.
   * If the queue has reached capacity, then
   * the fiber performing the `offerAll` will be suspended until there is room in
   * the queue.
   *
   * For Unbounded Queue:
   * Places all values in the queue and returns true.
   *
   * For Sliding Queue: uses `Sliding` Strategy
   * If there is room in the queue, it places the values otherwise it removes the old elements and
   * enqueues the new ones. Always returns true.
   *
   * For Dropping Queue: uses `Dropping` Strategy,
   * It places the values in the queue but if there is no room it will not enqueue them and return false.
   */
  def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean]

  /**
   * Interrupts any fibers that are suspended on `offer` or `take`.
   * Future calls to `offer*` and `take*` will be interrupted immediately.
   */
  def shutdown: UIO[Unit]

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  def size: UIO[Int]

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
   * Takes a number of elements from the queue between the specified minimum
   * and maximum. If there are fewer than the minimum number of elements
   * available, suspends until at least the minimum number of elements have
   * been collected.
   */
  final def takeBetween(min: Int, max: Int): ZIO[RB, EB, List[B]] =
    ZIO.effectSuspendTotal {
      val buffer = ListBuffer[B]()

      def takeRemainder(min: Int, max: Int): ZIO[RB, EB, Any] =
        if (max < min) ZIO.unit
        else
          takeUpTo(max).flatMap { bs =>
            val remaining = min - bs.length
            if (remaining == 1)
              take.flatMap(b => ZIO.effectTotal(buffer ++= bs += b))
            else if (remaining > 1) {
              take.flatMap { b =>
                ZIO.effectSuspendTotal {
                  buffer ++= bs += b
                  takeRemainder(remaining - 1, max - bs.length - 1)
                }
              }
            } else
              ZIO.effectTotal(buffer ++= bs)
          }

      takeRemainder(min, max).as(buffer.toList)
    }

  /**
   * Takes the specified number of elements from the queue.
   * If there are fewer than the specified number of elements available,
   * it suspends until they become available.
   */
  final def takeN(n: Int): ZIO[RB, EB, List[B]] =
    takeBetween(n, n)

  /**
   * Alias for `both`.
   */
  @deprecated("use ZStream", "2.0.0")
  final def &&[RA1 <: RA, RB1 <: RB, EA1 >: EA, EB1 >: EB, A1 <: A, C, D](
    that: ZQueue[RA1, RB1, EA1, EB1, A1, C]
  ): ZQueue[RA1, RB1, EA1, EB1, A1, (B, C)] =
    both(that)

  /**
   * Like `bothWith`, but tuples the elements instead of applying a function.
   */
  @deprecated("use ZStream", "2.0.0")
  final def both[RA1 <: RA, RB1 <: RB, EA1 >: EA, EB1 >: EB, A1 <: A, C, D](
    that: ZQueue[RA1, RB1, EA1, EB1, A1, C]
  ): ZQueue[RA1, RB1, EA1, EB1, A1, (B, C)] =
    bothWith(that)((_, _))

  /**
   * Like `bothWithM`, but uses a pure function.
   */
  @deprecated("use ZStream", "2.0.0")
  final def bothWith[RA1 <: RA, RB1 <: RB, EA1 >: EA, EB1 >: EB, A1 <: A, C, D](
    that: ZQueue[RA1, RB1, EA1, EB1, A1, C]
  )(f: (B, C) => D): ZQueue[RA1, RB1, EA1, EB1, A1, D] =
    bothWithM(that)((a, b) => IO.succeedNow(f(a, b)))

  /**
   * Creates a new queue from this queue and another. Offering to the composite queue
   * will broadcast the elements to both queues; taking from the composite queue
   * will dequeue elements from both queues and apply the function point-wise.
   *
   * Note that using queues with different strategies may result in surprising behavior.
   * For example, a dropping queue and a bounded queue composed together may apply `f`
   * to different elements.
   */
  @deprecated("use ZStream", "2.0.0")
  final def bothWithM[RA1 <: RA, RB1 <: RB, R3 <: RB1, EA1 >: EA, EB1 >: EB, E3 >: EB1, A1 <: A, C, D](
    that: ZQueue[RA1, RB1, EA1, EB1, A1, C]
  )(f: (B, C) => ZIO[R3, E3, D]): ZQueue[RA1, R3, EA1, E3, A1, D] =
    new ZQueue[RA1, R3, EA1, E3, A1, D] {
      def capacity: Int = math.min(self.capacity, that.capacity)

      def offer(a: A1): ZIO[RA1, EA1, Boolean]               = self.offer(a).zipWithPar(that.offer(a))(_ && _)
      def offerAll(as: Iterable[A1]): ZIO[RA1, EA1, Boolean] = self.offerAll(as).zipWithPar(that.offerAll(as))(_ && _)

      def awaitShutdown: UIO[Unit] = self.awaitShutdown *> that.awaitShutdown
      def size: UIO[Int]           = self.size.zipWithPar(that.size)(math.max)
      def shutdown: UIO[Unit]      = self.shutdown.zipWithPar(that.shutdown)((_, _) => ())
      def isShutdown: UIO[Boolean] = self.isShutdown.zipWithPar(that.isShutdown)(_ || _)
      def take: ZIO[R3, E3, D]     = self.take.zipPar(that.take).flatMap(f.tupled)

      def takeAll: ZIO[R3, E3, List[D]] =
        self.takeAll.zipPar(that.takeAll).flatMap { case (bs, cs) =>
          val bsIt = bs.iterator
          val csIt = cs.iterator

          ZIO.foreach(bsIt.zip(csIt).toList)(f.tupled)
        }

      def takeUpTo(max: Int): ZIO[R3, E3, List[D]] =
        self.takeUpTo(max).zipPar(that.takeUpTo(max)).flatMap { case (bs, cs) =>
          val bsIt = bs.iterator
          val csIt = cs.iterator

          ZIO.foreach(bsIt.zip(csIt).toList)(f.tupled)
        }
    }

  /**
   * Transforms elements enqueued into this queue with a pure function.
   */
  final def contramap[C](f: C => A): ZQueue[RA, RB, EA, EB, C, B] =
    contramapM(f andThen ZIO.succeedNow)

  /**
   * Transforms elements enqueued into this queue with an effectful function.
   */
  final def contramapM[RA2 <: RA, EA2 >: EA, C](f: C => ZIO[RA2, EA2, A]): ZQueue[RA2, RB, EA2, EB, C, B] =
    dimapM(f, ZIO.succeedNow)

  /**
   * Transforms elements enqueued into and dequeued from this queue with the
   * specified pure functions.
   */
  final def dimap[C, D](f: C => A, g: B => D): ZQueue[RA, RB, EA, EB, C, D] =
    dimapM(f andThen ZIO.succeedNow, g andThen ZIO.succeedNow)

  /**
   * Transforms elements enqueued into and dequeued from this queue with the
   * specified effectual functions.
   */
  final def dimapM[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZIO[RC, EC, A],
    g: B => ZIO[RD, ED, D]
  ): ZQueue[RC, RD, EC, ED, C, D] =
    new ZQueue[RC, RD, EC, ED, C, D] {
      def capacity: Int = self.capacity

      def offer(c: C): ZIO[RC, EC, Boolean] =
        f(c).flatMap(self.offer)

      def offerAll(cs: Iterable[C]): ZIO[RC, EC, Boolean] =
        ZIO.foreach(cs)(f).flatMap(self.offerAll)

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def isShutdown: UIO[Boolean]                 = self.isShutdown
      def take: ZIO[RD, ED, D]                     = self.take.flatMap(g)
      def takeAll: ZIO[RD, ED, List[D]]            = self.takeAll.flatMap(ZIO.foreach(_)(g))
      def takeUpTo(max: Int): ZIO[RD, ED, List[D]] = self.takeUpTo(max).flatMap(ZIO.foreach(_)(g))
    }

  /**
   * Applies a filter to elements enqueued into this queue. Elements that do not
   * pass the filter will be immediately dropped.
   */
  final def filterInput[A1 <: A](f: A1 => Boolean): ZQueue[RA, RB, EA, EB, A1, B] =
    filterInputM(f andThen ZIO.succeedNow)

  /**
   * Like `filterInput`, but uses an effectful function to filter the elements.
   */
  final def filterInputM[R2 <: RA, E2 >: EA, A1 <: A](f: A1 => ZIO[R2, E2, Boolean]): ZQueue[R2, RB, E2, EB, A1, B] =
    new ZQueue[R2, RB, E2, EB, A1, B] {
      def capacity: Int = self.capacity

      def offer(a: A1): ZIO[R2, E2, Boolean] =
        f(a) flatMap {
          if (_) self.offer(a)
          else IO.succeedNow(false)
        }

      def offerAll(as: Iterable[A1]): ZIO[R2, E2, Boolean] =
        ZIO.foreach(as)(a => f(a).map(if (_) Some(a) else None)).flatMap { maybeAs =>
          val filtered = maybeAs.flatten
          if (filtered.isEmpty) ZIO.succeedNow(false)
          else self.offerAll(filtered)
        }

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def isShutdown: UIO[Boolean]                 = self.isShutdown
      def take: ZIO[RB, EB, B]                     = self.take
      def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
      def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
    }

  /**
   * Filters elements dequeued from the queue using the specified predicate.
   */
  final def filterOutput(f: B => Boolean): ZQueue[RA, RB, EA, EB, A, B] =
    filterOutputM(b => ZIO.succeedNow(f(b)))

  /**
   * Filters elements dequeued from the queue using the specified effectual
   * predicate.
   */
  def filterOutputM[RB1 <: RB, EB1 >: EB](f: B => ZIO[RB1, EB1, Boolean]): ZQueue[RA, RB1, EA, EB1, A, B] =
    new ZQueue[RA, RB1, EA, EB1, A, B] {
      def awaitShutdown: UIO[Unit] =
        self.awaitShutdown
      def capacity: Int =
        self.capacity
      def isShutdown: UIO[Boolean] =
        self.isShutdown
      def offer(a: A): ZIO[RA, EA, Boolean] =
        self.offer(a)
      def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean] =
        self.offerAll(as)
      def shutdown: UIO[Unit] =
        self.shutdown
      def size: UIO[Int] =
        self.size
      def take: ZIO[RB1, EB1, B] =
        self.take.flatMap { b =>
          f(b).flatMap { p =>
            if (p) ZIO.succeedNow(b)
            else take
          }
        }
      def takeAll: ZIO[RB1, EB1, List[B]] =
        self.takeAll.flatMap(bs => ZIO.filter(bs)(f))
      def takeUpTo(max: Int): ZIO[RB1, EB1, List[B]] =
        ZIO.effectSuspendTotal {
          val buffer = ListBuffer[B]()
          def loop(max: Int): ZIO[RB1, EB1, Unit] =
            self.takeUpTo(max).flatMap { bs =>
              if (bs.isEmpty) ZIO.unit
              else
                ZIO.filter(bs)(f).flatMap { filtered =>
                  buffer ++= filtered
                  val length = filtered.length
                  if (length == max) ZIO.unit
                  else loop(max - length)
                }
            }
          loop(max).as(buffer.toList)
        }
    }

  /**
   * Transforms elements dequeued from this queue with a function.
   */
  final def map[C](f: B => C): ZQueue[RA, RB, EA, EB, A, C] =
    mapM(f andThen ZIO.succeedNow)

  /**
   * Transforms elements dequeued from this queue with an effectful function.
   */
  final def mapM[R2 <: RB, E2 >: EB, C](f: B => ZIO[R2, E2, C]): ZQueue[RA, R2, EA, E2, A, C] =
    dimapM(ZIO.succeedNow, f)

  /**
   * Take the head option of values in the queue.
   */
  final def poll: ZIO[RB, EB, Option[B]] =
    takeUpTo(1).map(_.headOption)
}

object ZQueue {
  private[zio] object internal {

    /**
     * Poll all items from the queue
     */
    def unsafePollAll[A](q: MutableConcurrentQueue[A]): List[A] = {
      val empty = null.asInstanceOf[A]

      @tailrec
      def poll(as: List[A]): List[A] =
        q.poll(empty) match {
          case null => as
          case a    => poll(a :: as)
        }

      poll(List.empty[A]).reverse
    }

    /**
     * Poll n items from the queue
     */
    def unsafePollN[A](q: MutableConcurrentQueue[A], max: Int): List[A] =
      q.pollUpTo(max).toList

    /**
     * Offer items to the queue
     */
    def unsafeOfferAll[A](q: MutableConcurrentQueue[A], as: List[A]): List[A] =
      q.offerAll(as).toList

    /**
     * Remove an item from the queue
     */
    def unsafeRemove[A](q: MutableConcurrentQueue[A], a: A): Unit = {
      unsafeOfferAll(q, unsafePollAll(q).filterNot(_ == a))
      ()
    }

    def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Unit =
      p.unsafeDone(IO.succeedNow(a))

    sealed abstract class Strategy[A] {
      def handleSurplus(
        as: List[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      ): UIO[Boolean]

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit

      def surplusSize: Int

      def shutdown: UIO[Unit]

      final def unsafeCompleteTakers(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = {
        // check if there is both a taker and an item in the queue, starting by the taker
        var keepPolling = true
        val nullTaker   = null.asInstanceOf[Promise[Nothing, A]]
        val empty       = null.asInstanceOf[A]

        while (keepPolling && !queue.isEmpty()) {
          val taker = takers.poll(nullTaker)
          if (taker eq nullTaker) keepPolling = false
          else {
            queue.poll(empty) match {
              case null =>
                unsafeOfferAll(takers, taker :: unsafePollAll(takers))
              case a =>
                unsafeCompletePromise(taker, a)
                unsafeOnQueueEmptySpace(queue)
            }
            keepPolling = true
          }
        }
      }
    }

    final case class Sliding[A]() extends Strategy[A] {
      def handleSurplus(
        as: List[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
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

        IO.effectTotal {
          unsafeSlidingOffer(as)
          unsafeCompleteTakers(queue, takers)
          true
        }
      }

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit = ()

      def surplusSize: Int = 0

      def shutdown: UIO[Unit] = IO.unit
    }

    final case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      def handleSurplus(
        as: List[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      ): UIO[Boolean] = IO.succeedNow(false)

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit = ()

      def surplusSize: Int = 0

      def shutdown: UIO[Unit] = IO.unit
    }

    final case class BackPressure[A]() extends Strategy[A] {
      // A is an item to add
      // Promise[Nothing, Boolean] is the promise completing the whole offerAll
      // Boolean indicates if it's the last item to offer (promise should be completed once this item is added)
      private val putters = MutableConcurrentQueue.unbounded[(A, Promise[Nothing, Boolean], Boolean)]

      private def unsafeRemove(p: Promise[Nothing, Boolean]): Unit = {
        unsafeOfferAll(putters, unsafePollAll(putters).filterNot(_._2 == p))
        ()
      }

      def handleSurplus(
        as: List[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      ): UIO[Boolean] =
        UIO.effectSuspendTotalWith { (_, fiberId) =>
          val p = Promise.unsafeMake[Nothing, Boolean](fiberId)

          UIO.effectSuspendTotal {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue)
            unsafeCompleteTakers(queue, takers)
            if (isShutdown.get) ZIO.interrupt else p.await
          }.onInterrupt(IO.effectTotal(unsafeRemove(p)))
        }

      @tailrec
      private def unsafeOffer(as: List[A], p: Promise[Nothing, Boolean]): Unit =
        as match {
          case Nil =>
          case head :: Nil =>
            putters.offer((head, p, true))
            ()
          case head :: tail =>
            putters.offer((head, p, false))
            unsafeOffer(tail, p)
        }

      def unsafeOnQueueEmptySpace(queue: MutableConcurrentQueue[A]): Unit = {
        val empty       = null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)]
        var keepPolling = true

        while (keepPolling && !queue.isFull()) {
          val putter = putters.poll(empty)
          if (putter eq null) keepPolling = false
          else {
            val offered = queue.offer(putter._1)
            if (offered && putter._3)
              unsafeCompletePromise(putter._2, true)
            else if (!offered)
              unsafeOfferAll(putters, putter :: unsafePollAll(putters))
          }
        }
      }

      def surplusSize: Int = putters.size()

      def shutdown: UIO[Unit] =
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
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ): Queue[A] = new ZQueue[Any, Any, Nothing, Nothing, A, A] {

    private def removeTaker(taker: Promise[Nothing, A]): UIO[Unit] = IO.effectTotal(unsafeRemove(takers, taker))

    val capacity: Int = queue.capacity

    def offer(a: A): UIO[Boolean] =
      UIO.effectSuspendTotal {
        if (shutdownFlag.get) ZIO.interrupt
        else {
          val noRemaining =
            if (queue.isEmpty()) {
              val nullTaker = null.asInstanceOf[Promise[Nothing, A]]
              val taker     = takers.poll(nullTaker)

              if (taker eq nullTaker) false
              else {
                unsafeCompletePromise(taker, a)
                true
              }
            } else false

          if (noRemaining) IO.succeedNow(true)
          else {
            // not enough takers, offer to the queue
            val succeeded = queue.offer(a)
            strategy.unsafeCompleteTakers(queue, takers)

            if (succeeded)
              IO.succeedNow(true)
            else
              strategy.handleSurplus(List(a), queue, takers, shutdownFlag)
          }
        }
      }

    def offerAll(as: Iterable[A]): UIO[Boolean] =
      UIO.effectSuspendTotal {
        if (shutdownFlag.get) ZIO.interrupt
        else {
          val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else List.empty
          val (forTakers, remaining) = as.splitAt(pTakers.size)
          (pTakers zip forTakers).foreach { case (taker, item) =>
            unsafeCompletePromise(taker, item)
          }

          if (remaining.isEmpty) IO.succeedNow(true)
          else {
            // not enough takers, offer to the queue
            val surplus = unsafeOfferAll(queue, remaining.toList)
            strategy.unsafeCompleteTakers(queue, takers)

            if (surplus.isEmpty)
              IO.succeedNow(true)
            else
              strategy.handleSurplus(surplus, queue, takers, shutdownFlag)
          }
        }
      }

    val awaitShutdown: UIO[Unit] = shutdownHook.await

    val size: UIO[Int] =
      UIO.effectSuspendTotal {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          UIO.succeedNow(queue.size() - takers.size() + strategy.surplusSize)
      }

    val shutdown: UIO[Unit] =
      UIO.effectSuspendTotalWith { (_, fiberId) =>
        shutdownFlag.set(true)

        UIO
          .whenM(shutdownHook.succeed(()))(
            UIO.foreachPar_(unsafePollAll(takers))(_.interruptAs(fiberId)) *> strategy.shutdown
          )
      }.uninterruptible

    val isShutdown: UIO[Boolean] = UIO(shutdownFlag.get)

    val take: UIO[A] =
      UIO.effectSuspendTotalWith { (_, fiberId) =>
        if (shutdownFlag.get) ZIO.interrupt
        else {
          queue.poll(null.asInstanceOf[A]) match {
            case null =>
              // add the promise to takers, then:
              // - try take again in case a value was added since
              // - wait for the promise to be completed
              // - clean up resources in case of interruption
              val p = Promise.unsafeMake[Nothing, A](fiberId)

              UIO.effectSuspendTotal {
                takers.offer(p)
                strategy.unsafeCompleteTakers(queue, takers)
                if (shutdownFlag.get) ZIO.interrupt else p.await
              }.onInterrupt(removeTaker(p))

            case item =>
              strategy.unsafeOnQueueEmptySpace(queue)
              IO.succeedNow(item)
          }
        }
      }

    val takeAll: UIO[List[A]] =
      UIO.effectSuspendTotal {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          IO.effectTotal {
            val as = unsafePollAll(queue)
            strategy.unsafeOnQueueEmptySpace(queue)
            as
          }
      }

    def takeUpTo(max: Int): UIO[List[A]] =
      UIO.effectSuspendTotal {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          IO.effectTotal {
            val as = unsafePollN(queue, max)
            strategy.unsafeOnQueueEmptySpace(queue)
            as
          }
      }
  }

  /**
   * Makes a new bounded queue.
   * When the capacity of the queue is reached, any additional calls to `offer` will be suspended
   * until there is more room in the queue.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity capacity of the `Queue`
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  def bounded[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, BackPressure()))

  /**
   * Makes a new bounded queue with the dropping strategy.
   * When the capacity of the queue is reached, new elements will be dropped.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity capacity of the `Queue`
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  def dropping[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Dropping()))

  /**
   * Makes a new bounded queue with sliding strategy.
   * When the capacity of the queue is reached, new elements will be added and the old elements
   * will be dropped.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance by utilising an optimised version of
   * the underlying [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity capacity of the `Queue`
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  def sliding[A](requestedCapacity: Int): UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Sliding()))

  /**
   * Makes a new unbounded queue.
   *
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  def unbounded[A]: UIO[Queue[A]] =
    IO.effectTotal(MutableConcurrentQueue.unbounded[A]).flatMap(createQueue(_, Dropping()))

  private def createQueue[A](queue: MutableConcurrentQueue[A], strategy: Strategy[A]): UIO[Queue[A]] =
    Promise
      .make[Nothing, Unit]
      .map(p =>
        unsafeCreate(
          queue,
          MutableConcurrentQueue.unbounded[Promise[Nothing, A]],
          p,
          new AtomicBoolean(false),
          strategy
        )
      )
}
