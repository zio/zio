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

import zio.internal.MutableConcurrentQueue
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean

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
  def awaitShutdown(implicit trace: ZTraceElement): UIO[Unit]

  /**
   * How many elements can hold in the queue
   */
  def capacity: Int

  /**
   * `true` if `shutdown` has been called.
   */
  def isShutdown(implicit trace: ZTraceElement): UIO[Boolean]

  /**
   * Places one value in the queue.
   */
  def offer(a: A)(implicit trace: ZTraceElement): ZIO[RA, EA, Boolean]

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
  def offerAll(as: Iterable[A])(implicit trace: ZTraceElement): ZIO[RA, EA, Boolean]

  /**
   * Interrupts any fibers that are suspended on `offer` or `take`.
   * Future calls to `offer*` and `take*` will be interrupted immediately.
   */
  def shutdown(implicit trace: ZTraceElement): UIO[Unit]

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  def size(implicit trace: ZTraceElement): UIO[Int]

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  def take(implicit trace: ZTraceElement): ZIO[RB, EB, B]

  /**
   * Removes all the values in the queue and returns the values. If the queue
   * is empty returns an empty collection.
   */
  def takeAll(implicit trace: ZTraceElement): ZIO[RB, EB, Chunk[B]]

  /**
   * Takes up to max number of values in the queue.
   */
  def takeUpTo(max: Int)(implicit trace: ZTraceElement): ZIO[RB, EB, Chunk[B]]

  /**
   * Takes a number of elements from the queue between the specified minimum
   * and maximum. If there are fewer than the minimum number of elements
   * available, suspends until at least the minimum number of elements have
   * been collected.
   */
  final def takeBetween(min: Int, max: Int)(implicit trace: ZTraceElement): ZIO[RB, EB, Chunk[B]] =
    ZIO.suspendSucceed {

      def takeRemainder(min: Int, max: Int, acc: Chunk[B]): ZIO[RB, EB, Chunk[B]] =
        if (max < min) ZIO.succeedNow(acc)
        else
          takeUpTo(max).flatMap { bs =>
            val remaining = min - bs.length
            if (remaining == 1)
              take.map(b => acc ++ bs :+ b)
            else if (remaining > 1) {
              take.flatMap { b =>
                takeRemainder(remaining - 1, max - bs.length - 1, acc ++ bs :+ b)

              }
            } else
              ZIO.succeedNow(acc ++ bs)
          }

      takeRemainder(min, max, Chunk.empty)
    }

  /**
   * Takes the specified number of elements from the queue.
   * If there are fewer than the specified number of elements available,
   * it suspends until they become available.
   */
  final def takeN(n: Int)(implicit trace: ZTraceElement): ZIO[RB, EB, Chunk[B]] =
    takeBetween(n, n)

  /**
   * Transforms elements enqueued into this queue with a pure function.
   */
  final def contramap[C](f: C => A): ZQueue[RA, RB, EA, EB, C, B] =
    contramapZIO(f andThen ZIO.succeedNow)

  /**
   * Transforms elements enqueued into this queue with an effectful function.
   */
  @deprecated("use contramapZIO", "2.0.0")
  final def contramapM[RA2 <: RA, EA2 >: EA, C](f: C => ZIO[RA2, EA2, A]): ZQueue[RA2, RB, EA2, EB, C, B] =
    contramapZIO(f)

  /**
   * Transforms elements enqueued into this queue with an effectful function.
   */
  final def contramapZIO[RA2 <: RA, EA2 >: EA, C](f: C => ZIO[RA2, EA2, A]): ZQueue[RA2, RB, EA2, EB, C, B] =
    dimapZIO(f, ZIO.succeedNow)

  /**
   * Transforms elements enqueued into and dequeued from this queue with the
   * specified pure functions.
   */
  final def dimap[C, D](f: C => A, g: B => D): ZQueue[RA, RB, EA, EB, C, D] =
    dimapZIO(f andThen ZIO.succeedNow, g andThen ZIO.succeedNow)

  /**
   * Transforms elements enqueued into and dequeued from this queue with the
   * specified effectual functions.
   */
  @deprecated("use dimapZIO", "2.0.0")
  final def dimapM[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZIO[RC, EC, A],
    g: B => ZIO[RD, ED, D]
  ): ZQueue[RC, RD, EC, ED, C, D] =
    dimapZIO(f, g)

  /**
   * Transforms elements enqueued into and dequeued from this queue with the
   * specified effectual functions.
   */
  final def dimapZIO[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZIO[RC, EC, A],
    g: B => ZIO[RD, ED, D]
  ): ZQueue[RC, RD, EC, ED, C, D] =
    new ZQueue[RC, RD, EC, ED, C, D] {
      def capacity: Int = self.capacity

      def offer(c: C)(implicit trace: ZTraceElement): ZIO[RC, EC, Boolean] =
        f(c).flatMap(self.offer)

      def offerAll(cs: Iterable[C])(implicit trace: ZTraceElement): ZIO[RC, EC, Boolean] =
        ZIO.foreach(cs)(f).flatMap(self.offerAll)

      def awaitShutdown(implicit trace: ZTraceElement): UIO[Unit]       = self.awaitShutdown
      def size(implicit trace: ZTraceElement): UIO[Int]                 = self.size
      def shutdown(implicit trace: ZTraceElement): UIO[Unit]            = self.shutdown
      def isShutdown(implicit trace: ZTraceElement): UIO[Boolean]       = self.isShutdown
      def take(implicit trace: ZTraceElement): ZIO[RD, ED, D]           = self.take.flatMap(g)
      def takeAll(implicit trace: ZTraceElement): ZIO[RD, ED, Chunk[D]] = self.takeAll.flatMap(ZIO.foreach(_)(g))
      def takeUpTo(max: Int)(implicit trace: ZTraceElement): ZIO[RD, ED, Chunk[D]] =
        self.takeUpTo(max).flatMap(ZIO.foreach(_)(g))
    }

  /**
   * Applies a filter to elements enqueued into this queue. Elements that do not
   * pass the filter will be immediately dropped.
   */
  final def filterInput[A1 <: A](f: A1 => Boolean): ZQueue[RA, RB, EA, EB, A1, B] =
    filterInputZIO(f andThen ZIO.succeedNow)

  /**
   * Like `filterInput`, but uses an effectful function to filter the elements.
   */
  @deprecated("use filterInputZIO", "2.0.0")
  final def filterInputM[R2 <: RA, E2 >: EA, A1 <: A](f: A1 => ZIO[R2, E2, Boolean]): ZQueue[R2, RB, E2, EB, A1, B] =
    filterInputZIO(f)

  /**
   * Like `filterInput`, but uses an effectful function to filter the elements.
   */
  final def filterInputZIO[R2 <: RA, E2 >: EA, A1 <: A](f: A1 => ZIO[R2, E2, Boolean]): ZQueue[R2, RB, E2, EB, A1, B] =
    new ZQueue[R2, RB, E2, EB, A1, B] {
      def capacity: Int = self.capacity

      def offer(a: A1)(implicit trace: ZTraceElement): ZIO[R2, E2, Boolean] =
        f(a) flatMap {
          if (_) self.offer(a)
          else IO.succeedNow(false)
        }

      def offerAll(as: Iterable[A1])(implicit trace: ZTraceElement): ZIO[R2, E2, Boolean] =
        ZIO.foreach(as)(a => f(a).map(if (_) Some(a) else None)).flatMap { maybeAs =>
          val filtered = maybeAs.flatten
          if (filtered.isEmpty) ZIO.succeedNow(false)
          else self.offerAll(filtered)
        }

      def awaitShutdown(implicit trace: ZTraceElement): UIO[Unit]                  = self.awaitShutdown
      def size(implicit trace: ZTraceElement): UIO[Int]                            = self.size
      def shutdown(implicit trace: ZTraceElement): UIO[Unit]                       = self.shutdown
      def isShutdown(implicit trace: ZTraceElement): UIO[Boolean]                  = self.isShutdown
      def take(implicit trace: ZTraceElement): ZIO[RB, EB, B]                      = self.take
      def takeAll(implicit trace: ZTraceElement): ZIO[RB, EB, Chunk[B]]            = self.takeAll
      def takeUpTo(max: Int)(implicit trace: ZTraceElement): ZIO[RB, EB, Chunk[B]] = self.takeUpTo(max)
    }

  /**
   * Filters elements dequeued from the queue using the specified predicate.
   */
  final def filterOutput(f: B => Boolean): ZQueue[RA, RB, EA, EB, A, B] =
    filterOutputZIO(b => ZIO.succeedNow(f(b)))

  /**
   * Filters elements dequeued from the queue using the specified effectual
   * predicate.
   */
  @deprecated("use filterOutputZIO", "2.0.0")
  def filterOutputM[RB1 <: RB, EB1 >: EB](f: B => ZIO[RB1, EB1, Boolean]): ZQueue[RA, RB1, EA, EB1, A, B] =
    filterOutputZIO(f)

  /**
   * Filters elements dequeued from the queue using the specified effectual
   * predicate.
   */
  def filterOutputZIO[RB1 <: RB, EB1 >: EB](f: B => ZIO[RB1, EB1, Boolean]): ZQueue[RA, RB1, EA, EB1, A, B] =
    new ZQueue[RA, RB1, EA, EB1, A, B] {
      def awaitShutdown(implicit trace: ZTraceElement): UIO[Unit] =
        self.awaitShutdown
      def capacity: Int =
        self.capacity
      def isShutdown(implicit trace: ZTraceElement): UIO[Boolean] =
        self.isShutdown
      def offer(a: A)(implicit trace: ZTraceElement): ZIO[RA, EA, Boolean] =
        self.offer(a)
      def offerAll(as: Iterable[A])(implicit trace: ZTraceElement): ZIO[RA, EA, Boolean] =
        self.offerAll(as)
      def shutdown(implicit trace: ZTraceElement): UIO[Unit] =
        self.shutdown
      def size(implicit trace: ZTraceElement): UIO[Int] =
        self.size
      def take(implicit trace: ZTraceElement): ZIO[RB1, EB1, B] =
        self.take.flatMap { b =>
          f(b).flatMap { p =>
            if (p) ZIO.succeedNow(b)
            else take
          }
        }
      def takeAll(implicit trace: ZTraceElement): ZIO[RB1, EB1, Chunk[B]] =
        self.takeAll.flatMap(bs => ZIO.filter(bs)(f))
      def takeUpTo(max: Int)(implicit trace: ZTraceElement): ZIO[RB1, EB1, Chunk[B]] =
        ZIO.suspendSucceed {
          def loop(max: Int, acc: Chunk[B]): ZIO[RB1, EB1, Chunk[B]] =
            self.takeUpTo(max).flatMap { bs =>
              if (bs.isEmpty) ZIO.succeedNow(acc)
              else
                ZIO.filter(bs)(f).flatMap { filtered =>
                  val length = filtered.length
                  if (length == max) ZIO.succeedNow(acc ++ filtered)
                  else loop(max - length, acc ++ filtered)
                }
            }
          loop(max, Chunk.empty)
        }
    }

  /**
   * Transforms elements dequeued from this queue with a function.
   */
  final def map[C](f: B => C): ZQueue[RA, RB, EA, EB, A, C] =
    mapZIO(f andThen ZIO.succeedNow)

  /**
   * Transforms elements dequeued from this queue with an effectful function.
   */
  @deprecated("use mapZIO", "2.0.0")
  final def mapM[R2 <: RB, E2 >: EB, C](f: B => ZIO[R2, E2, C]): ZQueue[RA, R2, EA, E2, A, C] =
    mapZIO(f)

  /**
   * Transforms elements dequeued from this queue with an effectful function.
   */
  final def mapZIO[R2 <: RB, E2 >: EB, C](f: B => ZIO[R2, E2, C]): ZQueue[RA, R2, EA, E2, A, C] =
    dimapZIO(ZIO.succeedNow, f)

  /**
   * Take the head option of values in the queue.
   */
  final def poll(implicit trace: ZTraceElement): ZIO[RB, EB, Option[B]] =
    takeUpTo(1).map(_.headOption)
}

object ZQueue {

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
  def bounded[A](requestedCapacity: Int)(implicit trace: ZTraceElement): UIO[Queue[A]] =
    IO.succeed(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Strategy.BackPressure()))

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
  def dropping[A](requestedCapacity: Int)(implicit trace: ZTraceElement): UIO[Queue[A]] =
    IO.succeed(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Strategy.Dropping()))

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
  def sliding[A](requestedCapacity: Int)(implicit trace: ZTraceElement): UIO[Queue[A]] =
    IO.succeed(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Strategy.Sliding()))

  /**
   * Makes a new unbounded queue.
   *
   * @tparam A type of the `Queue`
   * @return `UIO[Queue[A]]`
   */
  def unbounded[A](implicit trace: ZTraceElement): UIO[Queue[A]] =
    IO.succeed(MutableConcurrentQueue.unbounded[A]).flatMap(createQueue(_, Strategy.Dropping()))

  private def createQueue[A](queue: MutableConcurrentQueue[A], strategy: Strategy[A])(implicit
    trace: ZTraceElement
  ): UIO[Queue[A]] =
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

  private def unsafeCreate[A](
    queue: MutableConcurrentQueue[A],
    takers: MutableConcurrentQueue[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ): Queue[A] = new ZQueue[Any, Any, Nothing, Nothing, A, A] {

    private def removeTaker(taker: Promise[Nothing, A])(implicit trace: ZTraceElement): UIO[Unit] =
      IO.succeed(unsafeRemove(takers, taker))

    val capacity: Int = queue.capacity

    def offer(a: A)(implicit trace: ZTraceElement): UIO[Boolean] =
      UIO.suspendSucceed {
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
              strategy.handleSurplus(Chunk(a), queue, takers, shutdownFlag)
          }
        }
      }

    def offerAll(as: Iterable[A])(implicit trace: ZTraceElement): UIO[Boolean] =
      UIO.suspendSucceed {
        if (shutdownFlag.get) ZIO.interrupt
        else {
          val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else Chunk.empty
          val (forTakers, remaining) = as.splitAt(pTakers.size)
          (pTakers zip forTakers).foreach { case (taker, item) =>
            unsafeCompletePromise(taker, item)
          }

          if (remaining.isEmpty) IO.succeedNow(true)
          else {
            // not enough takers, offer to the queue
            val surplus = unsafeOfferAll(queue, remaining)
            strategy.unsafeCompleteTakers(queue, takers)

            if (surplus.isEmpty)
              IO.succeedNow(true)
            else
              strategy.handleSurplus(surplus, queue, takers, shutdownFlag)
          }
        }
      }

    def awaitShutdown(implicit trace: ZTraceElement): UIO[Unit] = shutdownHook.await

    def size(implicit trace: ZTraceElement): UIO[Int] =
      UIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          UIO.succeedNow(queue.size() - takers.size() + strategy.surplusSize)
      }

    def shutdown(implicit trace: ZTraceElement): UIO[Unit] =
      UIO.suspendSucceedWith { (_, fiberId) =>
        shutdownFlag.set(true)

        UIO
          .whenZIO(shutdownHook.succeed(()))(
            UIO.foreachParDiscard(unsafePollAll(takers))(_.interruptAs(fiberId)) *> strategy.shutdown
          )
          .unit
      }.uninterruptible

    def isShutdown(implicit trace: ZTraceElement): UIO[Boolean] = UIO(shutdownFlag.get)

    def take(implicit trace: ZTraceElement): UIO[A] =
      UIO.suspendSucceedWith { (_, fiberId) =>
        if (shutdownFlag.get) ZIO.interrupt
        else {
          queue.poll(null.asInstanceOf[A]) match {
            case null =>
              // add the promise to takers, then:
              // - try take again in case a value was added since
              // - wait for the promise to be completed
              // - clean up resources in case of interruption
              val p = Promise.unsafeMake[Nothing, A](fiberId)

              UIO.suspendSucceed {
                takers.offer(p)
                strategy.unsafeCompleteTakers(queue, takers)
                if (shutdownFlag.get) ZIO.interrupt else p.await
              }.onInterrupt(removeTaker(p))

            case item =>
              strategy.unsafeOnQueueEmptySpace(queue, takers)
              IO.succeedNow(item)
          }
        }
      }

    def takeAll(implicit trace: ZTraceElement): UIO[Chunk[A]] =
      UIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          IO.succeed {
            val as = unsafePollAll(queue)
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            as
          }
      }

    def takeUpTo(max: Int)(implicit trace: ZTraceElement): UIO[Chunk[A]] =
      UIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          IO.succeed {
            val as = unsafePollN(queue, max)
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            as
          }
      }
  }

  private sealed abstract class Strategy[A] {
    def handleSurplus(
      as: Iterable[A],
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]],
      isShutdown: AtomicBoolean
    )(implicit trace: ZTraceElement): UIO[Boolean]

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]]
    ): Unit

    def surplusSize: Int

    def shutdown(implicit trace: ZTraceElement): UIO[Unit]

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
              unsafeOfferAll(takers, taker +: unsafePollAll(takers))
            case a =>
              unsafeCompletePromise(taker, a)
              unsafeOnQueueEmptySpace(queue, takers)
          }
          keepPolling = true
        }
      }
    }
  }

  private object Strategy {

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
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: ZTraceElement): UIO[Boolean] =
        UIO.suspendSucceedWith { (_, fiberId) =>
          val p = Promise.unsafeMake[Nothing, Boolean](fiberId)

          UIO.suspendSucceed {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue, takers)
            unsafeCompleteTakers(queue, takers)
            if (isShutdown.get) ZIO.interrupt else p.await
          }.onInterrupt(IO.succeed(unsafeRemove(p)))
        }

      private def unsafeOffer(as: Iterable[A], p: Promise[Nothing, Boolean]): Unit =
        if (as.nonEmpty) {
          val iterator = as.iterator
          var a        = iterator.next()
          while (iterator.hasNext) {
            putters.offer((a, p, false))
            a = iterator.next()
          }
          putters.offer((a, p, true))
          ()
        }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = {
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
              unsafeOfferAll(putters, putter +: unsafePollAll(putters))
            unsafeCompleteTakers(queue, takers)
          }
        }
      }

      def surplusSize: Int = putters.size()

      def shutdown(implicit trace: ZTraceElement): UIO[Unit] =
        for {
          fiberId <- ZIO.fiberId
          putters <- IO.succeed(unsafePollAll(putters))
          _       <- IO.foreachPar(putters) { case (_, p, lastItem) => if (lastItem) p.interruptAs(fiberId) else IO.unit }
        } yield ()
    }

    final case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: ZTraceElement): UIO[Boolean] = IO.succeedNow(false)

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(implicit trace: ZTraceElement): UIO[Unit] = IO.unit
    }

    final case class Sliding[A]() extends Strategy[A] {
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: ZTraceElement): UIO[Boolean] = {
        def unsafeSlidingOffer(as: Iterable[A]): Unit =
          if (as.nonEmpty && queue.capacity > 0) {
            val iterator = as.iterator
            var a        = iterator.next()
            var loop     = true
            val empty    = null.asInstanceOf[A]
            while (loop) {
              queue.poll(empty)
              val offered = queue.offer(a)
              if (offered && iterator.hasNext) {
                a = iterator.next()
              } else if (offered && !iterator.hasNext) {
                loop = false
              }
            }
          }

        IO.succeed {
          unsafeSlidingOffer(as)
          unsafeCompleteTakers(queue, takers)
          true
        }
      }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(implicit trace: ZTraceElement): UIO[Unit] = IO.unit
    }
  }

  private def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Unit =
    p.unsafeDone(IO.succeedNow(a))

  /**
   * Offer items to the queue
   */
  private def unsafeOfferAll[A](q: MutableConcurrentQueue[A], as: Iterable[A]): Chunk[A] =
    q.offerAll(as)

  /**
   * Poll all items from the queue
   */
  private def unsafePollAll[A](q: MutableConcurrentQueue[A]): Chunk[A] =
    q.pollUpTo(Int.MaxValue)

  /**
   * Poll n items from the queue
   */
  private def unsafePollN[A](q: MutableConcurrentQueue[A], max: Int): Chunk[A] =
    q.pollUpTo(max)

  /**
   * Remove an item from the queue
   */
  private def unsafeRemove[A](q: MutableConcurrentQueue[A], a: A): Unit = {
    unsafeOfferAll(q, unsafePollAll(q).filterNot(_ == a))
    ()
  }
}
