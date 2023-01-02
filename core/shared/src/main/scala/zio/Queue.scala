/*
 * Copyright 2017-2023 John A. De Goes and the ZIO Contributors
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
 * A `Queue` is a lightweight, asynchronous queue into which values can be
 * enqueued and of which elements can be dequeued.
 */
abstract class Queue[A] extends Dequeue[A] with Enqueue[A] {

  /**
   * Checks whether the queue is currently empty.
   */
  override final def isEmpty(implicit trace: Trace): UIO[Boolean] =
    size.map(_ <= 0)

  /**
   * Checks whether the queue is currently full.
   */
  override final def isFull(implicit trace: Trace): UIO[Boolean] =
    size.map(_ >= capacity)
}

object Queue {

  /**
   * Makes a new bounded queue. When the capacity of the queue is reached, any
   * additional calls to `offer` will be suspended until there is more room in
   * the queue.
   *
   * @note
   *   when possible use only power of 2 capacities; this will provide better
   *   performance by utilising an optimised version of the underlying
   *   [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity
   *   capacity of the `Queue`
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def bounded[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.succeed(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Strategy.BackPressure()))

  /**
   * Makes a new bounded queue with the dropping strategy. When the capacity of
   * the queue is reached, new elements will be dropped.
   *
   * @note
   *   when possible use only power of 2 capacities; this will provide better
   *   performance by utilising an optimised version of the underlying
   *   [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity
   *   capacity of the `Queue`
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def dropping[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.succeed(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Strategy.Dropping()))

  /**
   * Makes a new bounded queue with sliding strategy. When the capacity of the
   * queue is reached, new elements will be added and the old elements will be
   * dropped.
   *
   * @note
   *   when possible use only power of 2 capacities; this will provide better
   *   performance by utilising an optimised version of the underlying
   *   [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity
   *   capacity of the `Queue`
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def sliding[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.succeed(MutableConcurrentQueue.bounded[A](requestedCapacity)).flatMap(createQueue(_, Strategy.Sliding()))

  /**
   * Makes a new unbounded queue.
   *
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def unbounded[A](implicit trace: Trace): UIO[Queue[A]] =
    ZIO.succeed(MutableConcurrentQueue.unbounded[A]).flatMap(createQueue(_, Strategy.Dropping()))

  private def createQueue[A](queue: MutableConcurrentQueue[A], strategy: Strategy[A])(implicit
    trace: Trace
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
  ): Queue[A] = new Queue[A] {

    private def removeTaker(taker: Promise[Nothing, A])(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafeRemove(takers, taker))

    val capacity: Int = queue.capacity

    def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
      ZIO.suspendSucceed {
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

          if (noRemaining) ZIO.succeedNow(true)
          else {
            // not enough takers, offer to the queue
            val succeeded = queue.offer(a)
            strategy.unsafeCompleteTakers(queue, takers)

            if (succeeded)
              ZIO.succeedNow(true)
            else
              strategy.handleSurplus(Chunk(a), queue, takers, shutdownFlag)
          }
        }
      }

    def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get) ZIO.interrupt
        else {
          val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else Chunk.empty
          val (forTakers, remaining) = as.splitAt(pTakers.size)
          (pTakers zip forTakers).foreach { case (taker, item) =>
            unsafeCompletePromise(taker, item)
          }

          if (remaining.isEmpty) ZIO.succeedNow(Chunk.empty)
          else {
            // not enough takers, offer to the queue
            val surplus = unsafeOfferAll(queue, remaining)
            strategy.unsafeCompleteTakers(queue, takers)

            if (surplus.isEmpty)
              ZIO.succeedNow(Chunk.empty)
            else
              strategy.handleSurplus(surplus, queue, takers, shutdownFlag).map { offered =>
                if (offered) Chunk.empty else surplus
              }
          }
        }
      }

    def awaitShutdown(implicit trace: Trace): UIO[Unit] = shutdownHook.await

    def size(implicit trace: Trace): UIO[Int] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          ZIO.succeedNow(queue.size() - takers.size() + strategy.surplusSize)
      }

    def shutdown(implicit trace: Trace): UIO[Unit] =
      ZIO.fiberIdWith { fiberId =>
        shutdownFlag.set(true)

        ZIO
          .whenZIO(shutdownHook.succeed(()))(
            ZIO.foreachParDiscard(unsafePollAll(takers))(_.interruptAs(fiberId)) *> strategy.shutdown
          )
          .unit
      }.uninterruptible

    def isShutdown(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(shutdownFlag.get)

    def take(implicit trace: Trace): UIO[A] =
      ZIO.fiberIdWith { fiberId =>
        if (shutdownFlag.get) ZIO.interrupt
        else {
          queue.poll(null.asInstanceOf[A]) match {
            case null =>
              // add the promise to takers, then:
              // - try take again in case a value was added since
              // - wait for the promise to be completed
              // - clean up resources in case of interruption
              val p = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe.unsafe)

              ZIO.suspendSucceed {
                takers.offer(p)
                strategy.unsafeCompleteTakers(queue, takers)
                if (shutdownFlag.get) ZIO.interrupt else p.await
              }.onInterrupt(removeTaker(p))

            case item =>
              strategy.unsafeOnQueueEmptySpace(queue, takers)
              ZIO.succeedNow(item)
          }
        }
      }

    def takeAll(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          ZIO.succeed {
            val as = unsafePollAll(queue)
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            as
          }
      }

    def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          ZIO.succeed {
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
    )(implicit trace: Trace): UIO[Boolean]

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]]
    ): Unit

    def surplusSize: Int

    def shutdown(implicit trace: Trace): UIO[Unit]

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
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          val p = Promise.unsafe.make[Nothing, Boolean](fiberId)(Unsafe.unsafe)

          ZIO.suspendSucceed {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue, takers)
            unsafeCompleteTakers(queue, takers)
            if (isShutdown.get) ZIO.interrupt else p.await
          }.onInterrupt(ZIO.succeed(unsafeRemove(p)))
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

      def shutdown(implicit trace: Trace): UIO[Unit] =
        for {
          fiberId <- ZIO.fiberId
          putters <- ZIO.succeed(unsafePollAll(putters))
          _       <- ZIO.foreachPar(putters) { case (_, p, lastItem) => if (lastItem) p.interruptAs(fiberId) else ZIO.unit }
        } yield ()
    }

    final case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = ZIO.succeedNow(false)

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }

    final case class Sliding[A]() extends Strategy[A] {
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = {
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

        ZIO.succeed {
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

      def shutdown(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }

  private def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Unit =
    p.unsafe.done(ZIO.succeedNow(a))(Unsafe.unsafe)

  /**
   * Offer items to the queue
   */
  private def unsafeOfferAll[A, B <: A](q: MutableConcurrentQueue[A], as: Iterable[B]): Chunk[B] =
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
